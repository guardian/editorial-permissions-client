package com.gu.editorial.permissions.client

import java.net.InetAddress
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object}
import com.gu.Box
import net.liftweb.json.DefaultFormats
import net.liftweb.json.JsonParser._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


case class PermissionsStoreModel(defaults: Seq[Permission], userOverrides: Map[String, PermissionsMap]) {
  val defaultsMap: PermissionsMap = defaults.map { p => (p, p.defaultValue) }.toMap
}

object PermissionsStoreModel {
  val empty = PermissionsStoreModel(Seq.empty, Map.empty)
}


class PermissionsStore(config: PermissionsConfig, provider: Option[PermissionsStoreProvider] = None)(implicit executionContext: ExecutionContext) {

  val storeProvider: PermissionsStoreProvider = provider.getOrElse(new PermissionsStoreFromS3(config))

  def get(perm: Permission)(implicit user: PermissionsUser): Future[PermissionAuthorisation] =
    list.map(_.getOrElse(perm, perm.defaultValue))


  def list(implicit user: PermissionsUser): Future[PermissionsMap] = {
    if (config.enablePermissionsStore)
      storeProvider.store.modify {
        case PermissionsStoreModel.empty => Failure(PermissionsStoreEmptyException())
        case s: PermissionsStoreModel => Success(s)
      }.map(s => s.defaultsMap ++ s.userOverrides.getOrElse(user.userId.toLowerCase, Map.empty))

    else Future.failed(PermissionsStoreDisabledException())
  }
}


trait PermissionsStoreProvider {
  val store: Box[PermissionsStoreModel]
  def storeIsEmpty: Boolean
}


private[client] final class PermissionsStoreFromS3(config: PermissionsConfig,
                                                   refreshFrequency: Option[FiniteDuration] = Some(Duration(1, MINUTES)),
                                                   s3Client: Option[AmazonS3] = None)
                                                  (implicit actorSystem: ActorSystem = ActorSystem(),
                                                   executionContext: ExecutionContext) extends PermissionsStoreProvider {

  implicit lazy val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout = Timeout(Duration(5, SECONDS))

  val store: Box[PermissionsStoreModel] = Box(PermissionsStoreModel.empty)

  def storeIsEmpty = {
    store.get() match {
      case PermissionsStoreModel.empty => true
      case s: PermissionsStoreModel => false
    }
  }

  private val s3 = s3Client.getOrElse {
    new AmazonS3(creds = config.awsCredentials, region = config.s3Region)
  }

  private lazy val refreshActor = actorSystem.actorOf(Props(classOf[PermissionsStoreRefreshActor], store, () => get, refreshFrequency, executionContext))

  private[client] def refreshStore =
    refreshActor ? RefreshStore

  private[client] def get: PermissionsStoreModel = S3Parser.parseFromS3 {
    val s3BucketAndPrefix = s"${config.s3Bucket}/${config.s3BucketPrefix}"
    logger.debug(s"Load permissions from S3 bucket: s3://$s3BucketAndPrefix/${config.s3PermissionsFile}")
    val (out, modDate) = s3.getContentsAndLastModified(config.s3PermissionsFile, s3BucketAndPrefix)
    logger.debug(s"Permissions successfully retrieved from S3, last modified: $modDate")
    out
  }

  if (refreshFrequency.isDefined) refreshStore
}

private[client] object S3Parser {
  def parseFromS3(input: String): PermissionsStoreModel = {
    import Implicits._

    implicit val formats = DefaultFormats

    case class PermissionOverrideForUser(userId: String, active: Boolean)
    case class PermissionCacheEntry(permission: SimplePermission, overrides: List[PermissionOverrideForUser])

    val parsed = parse(input).extract[List[PermissionCacheEntry]]

    val defaults: Seq[Permission] = parsed.map { p => p.permission : Permission }

    val values = for {
      entry <- parsed
      userOverride <- entry.overrides
    } yield (userOverride.userId.toLowerCase, entry.permission : Permission, userOverride.active : PermissionAuthorisation)

    val userOverrides = for {
      (userId, triple) <- values.groupBy(_._1)
      permMap = triple.map { e => e._2 -> e._3 }.toMap
    } yield userId -> permMap

    PermissionsStoreModel(defaults, userOverrides.toMap)
  }
}

private[client] final class PermissionsStoreRefreshActor(store: Box[PermissionsStoreModel],
                                                      get: () => PermissionsStoreModel,
                                                      refreshFrequency: Option[FiniteDuration])
                                                     (implicit executionContext: ExecutionContext) extends Actor with ActorLogging {

  override def receive: Receive = {
    case RefreshStore => {
      log.debug("Refresh permissions store")

      val s = sender()
      val update = try {
        store alter get()

      } catch {
        case NonFatal(err) => Future.failed {
          log.error(err, "Could not refresh permissions from S3")
          err
        }

      } finally {
        reschedule
      }

      update.onComplete {
        case Success(updatedStore) => s ! updatedStore
        case Failure(err) => s ! akka.actor.Status.Failure(err)
      }
    }
  }

  override def postRestart(reason: Throwable) = {
    reschedule
  }

  def reschedule = {
    refreshFrequency.map { frequency =>
      log.debug("Reschedule refresh permissions store actor")
      context.system.scheduler.scheduleOnce(frequency, self, RefreshStore)
    }
  }
}

private[client] case object RefreshStore


private[client] class AmazonS3(creds: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain, region: Option[String] = None) {

  lazy val awsClientConfigurationWithProxy = for {
    proxyHost <- Option(System.getProperty("http.proxyHost"))
    proxyPort <- Option(System.getProperty("http.proxyPort")).map(_.toInt)
  } yield {
      val config = new ClientConfiguration()
      config.setProxyHost(proxyHost)
      config.setProxyPort(proxyPort)
      config
    }

  lazy val awsClientConfiguration = awsClientConfigurationWithProxy.getOrElse(new ClientConfiguration())

  val s3Client = new AmazonS3Client(creds, awsClientConfiguration)

  lazy val isAWS = Try(InetAddress.getByName("instance-data")).isSuccess
  def awsOption[T](f: => T): Option[T] = if (isAWS) Option(f) else None

  // Regions.getCurrentRegion calls the EC2 metadata service (which hangs in GC2) hence we use the awsOption method
  private lazy val defaultRegion = awsOption(Regions.getCurrentRegion).getOrElse(Region.getRegion(Regions.EU_WEST_1))

  private val awsRegion = region.map { r => Region.getRegion(Regions.fromName(r)) }.getOrElse(defaultRegion)
  s3Client.setRegion(awsRegion)


  private def getObject(key: String, bucketName: String): S3Object =
    s3Client.getObject(new GetObjectRequest(bucketName, key))

  // Get object contents and ensure stream is closed
  def getObjectAsString(key: String, bucketName: String): String = {
    val obj = getObject(key, bucketName)
    try {
      Source.fromInputStream(obj.getObjectContent, "UTF-8").mkString
    } finally {
      obj.close()
    }
  }

  def getContentsAndLastModified(key: String, bucketName: String): (String, Date) = {
    val obj = getObject(key, bucketName)
    try {
      (Source.fromInputStream(obj.getObjectContent, "UTF-8").mkString, obj.getObjectMetadata.getLastModified)
    } finally {
      obj.close()
    }
  }
}
