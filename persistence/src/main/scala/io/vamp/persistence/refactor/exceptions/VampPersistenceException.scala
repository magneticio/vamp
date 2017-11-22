package io.vamp.persistence.refactor.exceptions

import io.vamp.common.{ Id, Namespace }
import io.vamp.persistence.refactor.serialization.SerializationSpecifier

/**
 * Created by mihai on 11/13/17.
 */
case class VampPersistenceModificationException[T](reason: String, objectId: Id[T])(implicit ns: Namespace, s: SerializationSpecifier[T]) extends Exception(s"Invalid-Modification-Exception in namespace ${ns.name}, ${s.typeName} -> ${objectId}; Reason: ${reason}")

case class DuplicateObjectIdException[T](objectId: Id[T])(implicit ns: Namespace, s: SerializationSpecifier[T]) extends Exception(s"Duplicate-ObjectId-Exception (second object remains uncreated) in namespace ${ns.name}, ${s.typeName} -> ${objectId}")

case class InvalidFormatException[T](objectAsString: String, originalException: io.circe.Error)(implicit ns: Namespace, s: SerializationSpecifier[T]) extends Exception(s"Invalid persistence exception ${originalException.getMessage} for object of type ${s.typeName}. Cannot correctly interpret ${objectAsString}.")

case class InvalidObjectIdException[T](objectId: Id[T])(implicit ns: Namespace, s: SerializationSpecifier[T]) extends Exception(s"Invalid-ObjectId-Exception in namespace ${ns.name}, ${s.typeName} -> ${objectId}")