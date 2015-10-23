package com.gu.editorial.permissions.client

import java.util.Date
import akka.actor.ActorSystem
import com.amazonaws.AmazonServiceException
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito.when
import scala.concurrent.{Awaitable, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class PermissionsStoreTest extends FunSuite with Matchers with MockitoSugar with BeforeAndAfterAll {

  implicit val config = PermissionsConfig("composer", Seq.empty)

  val testJson =
    """
      |[
      | {
      |   "permission":{"name":"launch_content","app":"composer","defaultValue":true},
      |   "overrides":[{"userId":"james","active":false}]
      | },
      | {
      |   "permission":{"name":"manage_users","app":"global","defaultValue":false},
      |   "overrides":[{"userId":"john.snow@guardian.co.uk","active":true}]
      | },
      | {
      |   "permission":{"name":"sensitivity_controls","app":"composer","defaultValue":false},
      |   "overrides":[]
      | },
      | {
      |   "permission":{"name":"delete_content","app":"composer","defaultValue":false},
      |   "overrides":[{"userId":"james","active":true},{"userId":"john.snow@guardian.co.uk","active":true}]
      | }
      |]
      |""".stripMargin

  val s3Mock = mock[AmazonS3]

  implicit val actorSystem = ActorSystem("PermissionsStoreTest")

  override def afterAll() = actorSystem.shutdown()

  def mockS3Response(response: String) =
    when(s3Mock.getContentsAndLastModified(config.s3PermissionsFile, s"${config.s3Bucket}/${config.s3BucketPrefix}")).thenReturn{ (response, new Date) }

  def await[T] = Await.result(_: Awaitable[T], Duration(10000, "millis"))

  val launchContentPermission = Permission("launch_content", "composer")
  val manageUsersPermission = Permission("manage_users", "global")
  val sensitivityControlsPermission = Permission("sensitivity_controls", "composer")
  val deleteContentPermission = Permission("delete_content", "composer")

  test("should parse from S3") {
    mockS3Response(testJson)
    val store = new PermissionsStoreFromS3(refreshFrequency = None, s3Client = Some(s3Mock))
    val result = store.get

    result.defaults.head should be(launchContentPermission)
    result.defaults(1) should be(manageUsersPermission)
    result.defaults(2) should be(sensitivityControlsPermission)
    result.defaults(3) should be(deleteContentPermission)
    result.defaults.size should be(4)

    result.defaults.head.defaultValue should be(PermissionGranted)
    result.defaults(1).defaultValue should be(PermissionDenied)
    result.defaults(2).defaultValue should be(PermissionDenied)
    result.defaults(3).defaultValue should be(PermissionDenied)

    val john = result.userOverrides.get("john.snow@guardian.co.uk")
    john should be('defined)

    john.get.get(launchContentPermission) should be(None)
    john.get.get(manageUsersPermission) should be(Some(PermissionGranted))

    val james = result.userOverrides.get("james")
    james should be('defined)

    james.get.get(launchContentPermission) should be(Some(PermissionDenied))
    james.get.get(sensitivityControlsPermission) should be(None)
  }

  test("should refresh store from S3") {
    mockS3Response(testJson)
    val storeProvider = new PermissionsStoreFromS3(refreshFrequency = None, s3Client = Some(s3Mock))

    val initStore = storeProvider.store.get()
    initStore.defaults should be(Seq.empty)
    initStore.userOverrides should be(Map.empty)

    await(storeProvider.refreshStore)

    val updatedStore = storeProvider.store.get()
    updatedStore.defaults should not be 'empty
    updatedStore.userOverrides should not be 'empty

    updatedStore.defaults.head should be(launchContentPermission)
    updatedStore.defaults should have size 4
    updatedStore.userOverrides.get("john.snow@guardian.co.uk") should be('defined)

    mockS3Response {
      """
        |[
        | {
        |   "permission": {"name":"go_crazy","app":"global","defaultValue":true}
        |   "overrides": [{"userId":"james","active":false}]
        | }
        |]
      """.stripMargin
    }

    await(storeProvider.refreshStore)

    val goCrazy = Permission("go_crazy", "global")
    val newUpdate = storeProvider.store.get()
    newUpdate.defaults.head should be(goCrazy)
    newUpdate.defaults should have size 1
    val jamesPerm = newUpdate.userOverrides.get("james")
    jamesPerm should be('defined)
    jamesPerm.get.get(goCrazy) should be(Some(PermissionDenied))
  }

  test("should fallback to defaults when overrides not present on a permissions list") {
    mockS3Response(testJson)

    val provider = new PermissionsStoreFromS3(refreshFrequency = None, s3Client = Some(s3Mock))
    val store = new PermissionsStore(Some(provider))

    await(provider.refreshStore)

    implicit val user = PermissionsUser("james", "")

    val permsList = await(store.list)

    permsList.get(manageUsersPermission) should be (Some(PermissionDenied))
    permsList.get(sensitivityControlsPermission) should be (Some(PermissionDenied))

    permsList should have size 4
  }

  // When S3 is down:
  test("should error when store not populated") {

    when(s3Mock.getContentsAndLastModified(config.s3PermissionsFile, s"${config.s3Bucket}/${config.s3BucketPrefix}")).thenThrow(new AmazonServiceException("Mocked AWS Exception"))

    val provider = new PermissionsStoreFromS3(s3Client = Some(s3Mock))
    val store = new PermissionsStore(Some(provider))

    implicit val user = PermissionsUser("james", "")

    intercept[PermissionsStoreEmptyException] {
      await(store.list)
    }
  }

}

