package com.gu.editorial.permissions

import scala.language.implicitConversions

/**
 * Permissions client library - retrieves and provides access control based on a permissions service.
 */
package object client {

  object Implicits {

    implicit def permissionAuthorisationToBoolean(ref: PermissionAuthorisation): Boolean = ref match {
      case PermissionGranted => true
      case PermissionDenied => false
    }

    implicit def booleanToPermissionAuthorisation(ref: Boolean): PermissionAuthorisation =
      if (ref) PermissionGranted else PermissionDenied

    implicit def simplePermissionToPermission(ref: SimplePermission): Permission =
      Permission(ref.name, ref.app, ref.defaultValue)

    implicit def permissionsMapToSimplePermissionsMap(ref: PermissionsMap): SimplePermissionsMap =
      ref.map { e => (e._1.name, e._2 : Boolean) }
  }

  sealed trait PermissionAuthorisation

  case object PermissionGranted extends PermissionAuthorisation
  case object PermissionDenied extends PermissionAuthorisation

  case class Permission(name: String, app: String) {
    val defaultValue: PermissionAuthorisation = PermissionDenied
  }

  object Permission {
    def apply(name: String, app: String, defaultVal: PermissionAuthorisation): Permission = new Permission(name, app) {
      override val defaultValue = defaultVal
    }
  }

  case class SimplePermission(name: String, app: String, defaultValue: Boolean = true)

  type PermissionsMap = Map[Permission, PermissionAuthorisation]

  /** Simple map for JSON serialisation */
  type SimplePermissionsMap = Map[String, Boolean]

  case class PermissionsUser(userId: String)

  case class PermissionsStoreDisabledException() extends Exception("Permissions Store disabled")

  case class PermissionsStoreEmptyException() extends Exception("Permissions Store empty or not yet populated")

  case class PermissionDeniedException(message: String = "Permission denied") extends Exception(message)

}
