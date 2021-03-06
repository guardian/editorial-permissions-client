package com.gu.editorial.permissions.client

import java.util.Date
import akka.actor.ActorSystem
import com.amazonaws.AmazonServiceException
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.when
import scala.concurrent.ExecutionContext.Implicits.global

class PermissionsStoreTest extends FunSuite with Matchers with MockitoSugar with BeforeAndAfterAll with ScalaFutures {

  val config = PermissionsConfig("composer", Seq.empty)

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

  override def afterAll() = actorSystem.terminate()

  def mockS3Response(response: String) =
    when(s3Mock.getContentsAndLastModified(config.s3PermissionsFile, s"${config.s3Bucket}/${config.s3BucketPrefix}")).thenReturn{ (response, new Date) }

  val launchContentPermission = Permission("launch_content", "composer")
  val manageUsersPermission = Permission("manage_users", "global")
  val sensitivityControlsPermission = Permission("sensitivity_controls", "composer")
  val deleteContentPermission = Permission("delete_content", "composer")

  test("should parse from S3") {
    mockS3Response(testJson)
    val store = new PermissionsStoreFromS3(config, refreshFrequency = None, s3Client = Some(s3Mock))
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
    val storeProvider = new PermissionsStoreFromS3(config, refreshFrequency = None, s3Client = Some(s3Mock))

    val initStore = storeProvider.store.get()
    initStore.defaults should be(Seq.empty)
    initStore.userOverrides should be(Map.empty)

    storeProvider.refreshStore.futureValue

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

    storeProvider.refreshStore.futureValue

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

    val provider = new PermissionsStoreFromS3(config, refreshFrequency = None, s3Client = Some(s3Mock))
    val store = new PermissionsStore(config, Some(provider))

    provider.refreshStore.futureValue

    implicit val user = PermissionsUser("james")

    whenReady(store.list) { permsList =>
      permsList.get(manageUsersPermission) should be(Some(PermissionDenied))
      permsList.get(sensitivityControlsPermission) should be(Some(PermissionDenied))

      permsList should have size 4
    }
  }

  test("should retrieve permissions for user ignoring email case") {
    mockS3Response {
      """
        |[
        | {
        |   "permission":{"name":"launch_content","app":"composer","defaultValue":true},
        |   "overrides":[{"userId":"joHn.sNow@Guardian.co.uk","active":false}]
        | },
        | {
        |   "permission": {"name":"delete_content","app":"composer","defaultValue":false}
        |   "overrides": [{"userId":"jOhn.snOw@guardian.co.uk","active":true}]
        | }
        |]
      """.stripMargin
    }

    val provider = new PermissionsStoreFromS3(config, refreshFrequency = None, s3Client = Some(s3Mock))
    val store = new PermissionsStore(config, Some(provider))

    provider.refreshStore.futureValue

    implicit val user = PermissionsUser("John.Snow@guardian.co.uk")

    whenReady(store.list) { permsList =>
      permsList.get(launchContentPermission) should be(Some(PermissionDenied))
      permsList.get(deleteContentPermission) should be(Some(PermissionGranted))

      permsList should have size 2
    }
  }




  // When S3 is down:
  test("should error when store not populated") {

    when(s3Mock.getContentsAndLastModified(config.s3PermissionsFile, s"${config.s3Bucket}/${config.s3BucketPrefix}")).thenThrow(new AmazonServiceException("Mocked AWS Exception"))

    val provider = new PermissionsStoreFromS3(s3Client = Some(s3Mock), config = config)
    val store = new PermissionsStore(config, Some(provider))

    implicit val user = PermissionsUser("james")

    store.list.failed.futureValue shouldBe a[PermissionsStoreEmptyException]
  }

}

