# editorial-permissions-client

Scala Client library for the Guardian's [Editorial Permissions service](https://github.com/guardian/permissions).

## Usage
Add the following dependency to your `build.sbt`

```scala
libraryDependencies += "com.gu" %% "editorial-permissions-client" % "0.1"
```

Then mixin the `PermissionsProvider` trait to configure integration with
`PermissionsConfig` and all your application Permissions by defining `val all: Seq[Permission]`.

For example:

```scala
import com.gu.editorial.permissions.client._
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
```

## Contributing

### Releasing

Ensure tests pass before a release.

    sbt clean test

Release versioning uses the latest git tag, with semantic versioning.

    git tag -t v0.1.1
    git push --tags

Note: use `sbt version` to double check the version before publishing.

Then release to sonatype using [`sbt-sonatype`](https://github.com/xerial/sbt-sonatype). Note you will need:

  - access to the `com.gu` group in Sonatype
  - a gpg key with public key sent to http://pgp.mit.edu/ see [sbt-pgp](http://www.scala-sbt.org/sbt-pgp)
  - your Sonatype credentials accessible to SBT using [sbt-sonatype](https://github.com/xerial/sbt-sonatype#homesbtsbt-versionsonatypesbt)

```
sbt
> clean
> publishSigned
> sonatypeRelease
```
