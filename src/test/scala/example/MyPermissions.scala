package example

import com.gu.editorial.permissions.client._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MyPermissions extends PermissionsProvider {
  val app = "composer"

  implicit def config = PermissionsConfig(
    app = app,
    all = all
  )

  val LaunchContent = Permission("launch_content", app, PermissionGranted)

  val all = Seq(LaunchContent)
}

object Example {

  implicit def permissionsUser: PermissionsUser =
    PermissionsUser("user.email@guardian.co.uk", "user-token")

  MyPermissions.get(MyPermissions.LaunchContent).map {
    case PermissionGranted => "I'm in!"
    case PermissionDenied => ":("
  }

  val myPerms: Future[PermissionsMap] = MyPermissions.list
}
