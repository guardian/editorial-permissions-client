package com.gu.editorial.permissions.client

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


trait PermissionsProvider {

  implicit lazy val logger = LoggerFactory.getLogger(getClass)

  implicit lazy val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def config: PermissionsConfig

  val store: PermissionsStore = new PermissionsStore(config)

  def get(perm: Permission)(implicit user: PermissionsUser): Future[PermissionAuthorisation] =
    store.get(perm).recover {
      case NonFatal(t) => {
        logger.error(s"Couldn't retrieve permission ${perm.name}, falling back to default", t)
        perm.defaultValue
      }
    }


  def getEither(perm: Permission)(implicit user: PermissionsUser): Future[Either[PermissionAuthorisation, PermissionAuthorisation]] =
    get(perm).map {
      case p @ PermissionGranted => Right(p)
      case p @ PermissionDenied => Left(p)
    }


  def requirePermission(perm: Permission)(implicit user: PermissionsUser): Future[PermissionAuthorisation] =
    get(perm).map {
      case p @ PermissionGranted => p
      case p @ PermissionDenied => throw PermissionDeniedException()
    }


  def list(implicit user: PermissionsUser): Future[PermissionsMap] =
    store.list
      .recover {
      case NonFatal(t) => {
        logger.error(s"Couldn't retrieve permission list for user ${user.userId}, falling back to default", t)
        config.all.map(p => { (p, p.defaultValue) }).toMap
      }
    }


  def listSimple(implicit user: PermissionsUser): Future[SimplePermissionsMap] = {
    import Implicits._
    list.map { p => p : SimplePermissionsMap }
  }
}

