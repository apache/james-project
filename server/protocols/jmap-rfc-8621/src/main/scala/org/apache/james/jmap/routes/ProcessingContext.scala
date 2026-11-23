/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.routes

import java.util.concurrent.atomic.AtomicLong

import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.NonNegInt
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ClientId, CreatedIds, Id, IdOrCreationReference, Invocation, ServerId}
import org.apache.james.jmap.json.BackReferenceDeserializer
import play.api.libs.json.{IdxPathNode, JsArray, JsError, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, JsonValidationError, KeyPathNode, PathNode, Reads}

import scala.collection.IndexedSeq
import scala.collection.Map
import scala.collection.immutable.{Map => ImmutableMap}
import scala.util.Try

sealed trait JsonPathPart

case object WildcardPart extends JsonPathPart

case class PlainPart(name: String) extends JsonPathPart {
  def read(jsValue: JsValue): JsResult[JsValue] = jsValue match {
    case JsObject(underlying) => underlying.get(name).map(JsSuccess(_)).getOrElse(JsError(s"Expected path $name was missing"))
    case array: JsArray => ArrayElementPart.parse(name)
      .map(_.read(array))
      .getOrElse(JsError("Expecting a JsObject but got a different structure"))
    case _ => JsError("Expecting a JsObject but got a different structure")
  }
}

object ArrayElementPart {
  def parse(string: String): Option[ArrayElementPart] = {
    if (string forall Character.isDigit) {
      Try(string.toInt)
        .fold(_ => None, fromInt)
    } else {
      None
    }
  }

  private def fromInt(position: Int): Option[ArrayElementPart] =
    refineV[NonNegative](position)
      .fold(_ => None,
        ref => Some(ArrayElementPart(ref)))
}

case class ArrayElementPart(position: NonNegInt) extends JsonPathPart {
  def read(jsValue: JsValue): JsResult[JsValue] = jsValue match {
    case JsArray(values) => values.lift(position.value)
      .map(JsSuccess(_))
      .getOrElse(JsError(s"Supplied array have no $position element"))
    case _ => JsError("Expecting a JsArray but got a different structure")
  }
}

object JsonPath {
  def parse(string: String): JsonPath = JsonPath(string.split('/').toList
    .flatMap {
      case "" => Nil
      case "*" => List(WildcardPart)
      case part: String => List(PlainPart(part.replace("~1", "/").replace("~0", "~")))
    })
}

case class JsonPath(parts: List[JsonPathPart]) {
  def evaluate(jsValue: JsValue): JsResult[JsValue] = parts match {
    case Nil => JsSuccess(jsValue)
    case head :: tail =>
      val tailAsJsonPath = JsonPath(tail)
      head match {
        case part: PlainPart => part.read(jsValue).flatMap(tailAsJsonPath.evaluate)
        case part: ArrayElementPart => part.read(jsValue).flatMap(tailAsJsonPath.evaluate)
        case WildcardPart => tailAsJsonPath.readWildcard(jsValue)
      }
  }

  private def readWildcard(jsValue: JsValue): JsResult[JsValue] = jsValue match {
    case JsArray(arrayItems) =>
      val evaluationResults: IndexedSeq[JsResult[JsValue]] = arrayItems.map(evaluate)

      evaluationResults.find(_.isInstanceOf[JsError])
        .getOrElse(JsSuccess(expendArray(evaluationResults)))
    case _ => JsError("Expecting an array")
  }

  private def expendArray(evaluationResults: IndexedSeq[JsResult[JsValue]]): JsArray =
    JsArray(evaluationResults
      .map(_.get)
      .flatMap({
        case JsArray(nestedArray) => nestedArray
        case other: JsValue => List(other)
      }))
}

case class BackReference(name: MethodName, path: JsonPath, resultOf: MethodCallId) {
  def resolve(invocation: Invocation): JsResult[JsValue] = if (!(invocation.methodName equals name)) {
    JsError(s"$resultOf references a ${invocation.methodName} method")
  } else {
    path.evaluate(invocation.arguments.value)
  }
}

sealed trait ArgumentResolutionError {
  def message: String
}

case class InvalidResultReferenceException(message: String) extends IllegalArgumentException with ArgumentResolutionError

case class DuplicateArgumentException(argument: String) extends IllegalArgumentException with ArgumentResolutionError {
  override val message: String = s"Argument '$argument' is supplied both directly and as a result reference"
}

case class AmbiguousArgumentException(path: JsPath, key: String) extends IllegalArgumentException with ArgumentResolutionError {
  override val message: String = s"Several entries of '${path.toJsonString}' resolve to '$key'"
}

object AmbiguousArgumentException {
  val ERROR_KEY: String = "error.ambiguous.argument"
}

/**
 * Argument locations where an Id is expected (RFC 8620 §5.3): creation references (`#creationId`) are only resolved there.
 */
trait IdPaths {
  /** Whether the string at `path`, or the array element at `path`, is an Id. */
  def isIdValue(path: JsPath): Boolean

  /** Whether the keys of the object at `objectPath` are Ids. */
  def hasIdKeys(objectPath: JsPath): Boolean
}

object IdPaths {
  /**
   * Id locations found by the naming of the JMAP specifications, at any depth: `id` and `*Id` string values, the
   * elements of `ids` and `*Ids` arrays, the keys of `*Ids` objects, plus `anchor`, `inMailbox`, the elements of
   * `inMailboxOtherThan`, the `update` keys and the `destroy` entries.
   *
   * String-typed properties named like Ids (`partId`, `deviceClientId`, `originalMessageId`), `header:` properties and
   * free-form `extensionFields` are not Id locations.
   */
  val CONVENTIONAL: IdPaths = new IdPaths {
    override def isIdValue(path: JsPath): Boolean = !isExcluded(path.path) && (path.path match {
      case List(KeyPathNode("destroy"), IdxPathNode(_)) => true
      case nodes => nodes.reverse match {
        case KeyPathNode(name) :: _ => isIdName(name)
        case IdxPathNode(_) :: KeyPathNode(name) :: _ => isIdArrayName(name)
        case _ => false
      }
    })

    override def hasIdKeys(objectPath: JsPath): Boolean = !isExcluded(objectPath.path) && (objectPath.path match {
      case List(KeyPathNode("update")) => true
      case nodes => nodes.lastOption.exists {
        case KeyPathNode(name) => name.endsWith("Ids")
        case _ => false
      }
    })
  }

  /** No creation reference is resolved, e.g. for `Core/echo` which returns its arguments unchanged (RFC 8620 §4). */
  val NONE: IdPaths = new IdPaths {
    override def isIdValue(path: JsPath): Boolean = false

    override def hasIdKeys(objectPath: JsPath): Boolean = false
  }

  private val STRING_PROPERTIES: Set[String] = Set("partId", "deviceClientId", "originalMessageId")

  private def isIdName(name: String): Boolean =
    !STRING_PROPERTIES.contains(name) && (name == "id" || name.endsWith("Id") || name == "anchor" || name == "inMailbox")

  private def isIdArrayName(name: String): Boolean = name == "ids" || name.endsWith("Ids") || name == "inMailboxOtherThan"

  private def isExcluded(nodes: List[PathNode]): Boolean = nodes.exists {
    case KeyPathNode(name) => name == "extensionFields" || name.startsWith("header:")
    case _ => false
  }
}

/**
 * One resolution phase. Result references are expanded first; creation references are then resolved on the expanded
 * arguments (RFC 8620 §3.7), at Id locations except the deferred ones.
 */
private case class ResolutionPolicy(resultReferences: Boolean, creationReferences: Boolean, deferred: Set[JsPath], idPaths: IdPaths) {
  def resolvesValue(path: JsPath): Boolean = creationReferences && !deferred.contains(path) && idPaths.isIdValue(path)

  def resolvesKey(objectPath: JsPath, key: String): Boolean = creationReferences && !deferred.contains(objectPath \ key) && idPaths.hasIdKeys(objectPath)
}

private object ResolutionPolicy {
  val RESULT_REFERENCES: ResolutionPolicy = ResolutionPolicy(resultReferences = true, creationReferences = false, Set.empty, IdPaths.NONE)

  def creationReferences(deferred: Set[JsPath], idPaths: IdPaths): ResolutionPolicy =
    ResolutionPolicy(resultReferences = false, creationReferences = true, deferred, idPaths)
}

object JsonSize {
  /** An estimate of the serialized size of the value, computed up to the given limit. */
  def upTo(value: JsValue, limit: Long): Long = {
    var size = 0L
    var pending: List[JsValue] = List(value)
    while (pending.nonEmpty && size <= limit) {
      val head = pending.head
      pending = pending.tail
      head match {
        case JsObject(underlying) =>
          size += 2
          underlying.foreach { case (key, entryValue) =>
            size += key.length + 4
            pending = entryValue :: pending
          }
        case JsArray(values) =>
          size += 2 + values.size
          pending = values.toList ++ pending
        case JsString(string) => size += string.length + 2
        // Numbers, booleans and null
        case _ => size += 8
      }
    }
    size
  }
}

/**
 * Shared by the contexts of one request: bounds what the resolution of its result references produces.
 * It carries no data of the request, so it plays no part in the equality of contexts.
 */
class ResultReferenceBudget(max: Long) {
  private val used: AtomicLong = new AtomicLong(0)

  def consume(value: JsValue): Boolean = {
    val remaining = max - used.get()
    used.addAndGet(JsonSize.upTo(value, remaining)) <= max
  }

  override def equals(other: Any): Boolean = other.isInstanceOf[ResultReferenceBudget]

  override def hashCode(): Int = 0
}

object ProcessingContext {
  val MAX_RESOLVED_RESULT_REFERENCES_SIZE: Long = 10L * 1024 * 1024
}

case class ProcessingContext(private val creationIds: ImmutableMap[ClientId, ServerId],
                             private val invocations: ImmutableMap[MethodCallId, Invocation],
                             private val resultReferenceBudget: ResultReferenceBudget = new ResultReferenceBudget(ProcessingContext.MAX_RESOLVED_RESULT_REFERENCES_SIZE)) {

  def createdIds: CreatedIds = CreatedIds(creationIds)

  def recordCreatedId(clientId: ClientId, serverId: ServerId): ProcessingContext =
    ProcessingContext(creationIds + (clientId -> serverId), invocations, resultReferenceBudget)

  def recordInvocation(invocation: Invocation): ProcessingContext =
    ProcessingContext(creationIds, invocations + (invocation.methodCallId -> invocation), resultReferenceBudget)

  def resolveBackReferences(invocation: Invocation): Either[ArgumentResolutionError, Invocation] =
    resolveBackReferences(invocation, Set.empty)

  /** Keys of the `create` argument, including one supplied through a `#create` result reference. */
  def declaredCreationIds(arguments: JsObject): Set[String] = {
    val create: Option[JsValue] = (arguments \ "create").toOption
      .orElse((arguments \ "#create").toOption
        .flatMap(value => BackReferenceDeserializer.deserializeBackReference(value).asOpt)
        .flatMap(backReference => resolve(backReference).asOpt))
    create.flatMap(_.asOpt[JsObject]).map(_.keys.toSet).getOrElse(Set.empty)
  }

  def resolveBackReferences(invocation: Invocation, deferredCreationReferences: Set[JsPath]): Either[ArgumentResolutionError, Invocation] =
    resolveBackReferences(invocation, deferredCreationReferences, IdPaths.CONVENTIONAL)

  def resolveBackReferences(invocation: Invocation, deferredCreationReferences: Set[JsPath], idPaths: IdPaths): Either[ArgumentResolutionError, Invocation] =
    resolveResultReferences(invocation)
      .flatMap(expanded => resolveCreationReferences(expanded, deferredCreationReferences, idPaths))

  /** Expands the result references of the arguments, leaving creation references as they are. */
  def resolveResultReferences(invocation: Invocation): Either[ArgumentResolutionError, Invocation] = {
    val argumentNames = invocation.arguments.value.keys
    argumentNames.find(key => key.startsWith("#") && argumentNames.contains(key.substring(1))) match {
      case Some(key) => Left(DuplicateArgumentException(key.substring(1)))
      case None => resolve(invocation, ResolutionPolicy.RESULT_REFERENCES)
    }
  }

  /** Resolves creation references at the Id locations of arguments whose result references are already expanded. */
  def resolveCreationReferences(invocation: Invocation, deferredCreationReferences: Set[JsPath], idPaths: IdPaths): Either[ArgumentResolutionError, Invocation] =
    resolve(invocation, ResolutionPolicy.creationReferences(deferredCreationReferences, idPaths))

  private def resolve(invocation: Invocation, policy: ResolutionPolicy): Either[ArgumentResolutionError, Invocation] =
    backReferenceResolver(JsPath, policy).reads(invocation.arguments.value) match {
      case JsError(errors) => Left(errors.view
        .flatMap { case (path, validationErrors) => validationErrors.find(_.message == AmbiguousArgumentException.ERROR_KEY)
          .map(error => AmbiguousArgumentException(path, error.args.head.toString)) }
        .headOption
        .getOrElse(InvalidResultReferenceException(errors.toString())))
      case JsSuccess(arguments: JsObject, _) => Right(invocation.copy(arguments = Arguments(arguments)))
      case others: JsSuccess[JsValue] => Left(InvalidResultReferenceException(s"Unexpected value $others"))
    }

  private def backReferenceResolver(path: JsPath, policy: ResolutionPolicy): Reads[JsValue] = {
    case JsArray(value) => resolveBackReferences(value, path, policy)
    case JsObject(underlying) => resolveBackReference(underlying, path, policy)
    case value: JsString if !policy.resolvesValue(path) => JsSuccess(value)
    case JsString(value) if value.startsWith("#") => resolveCreationId(value)
        .fold(_ => JsSuccess(JsString(value)),
          serverId => JsSuccess(JsString(serverId.value.value)))
    case others: JsValue => JsSuccess(others)
  }

  private def resolveBackReferences(array: IndexedSeq[JsValue], path: JsPath, policy: ResolutionPolicy): JsResult[JsValue] = {
    val results: IndexedSeq[JsResult[JsValue]] = array.zipWithIndex.map { case (value, index) =>
      backReferenceResolver(path(index), policy).reads(value)
    }
    results.find(_.isError)
      .getOrElse(JsSuccess(JsArray(results.map(_.get))))
  }

  private def resolveBackReference(underlying: Map[String, JsValue], path: JsPath, policy: ResolutionPolicy): JsResult[JsObject] = {
    val resolutions = underlying.map(entry => resolveBackReference(entry, path, policy))

    val firstError = resolutions.flatMap(_.left.toOption).headOption

    val transformedEntries = resolutions.flatMap(_.toOption).toSeq
    // Two entries resolving to the same key would silently drop one of them.
    val ambiguousKey = transformedEntries.groupBy(_._1).collectFirst { case (key, entries) if entries.size > 1 => key }

    firstError
      .orElse(ambiguousKey.map(key => JsError(path, JsonValidationError(AmbiguousArgumentException.ERROR_KEY, key))))
      .getOrElse(JsSuccess(JsObject(transformedEntries)))
  }

  private def resolveBackReference(entry: (String, JsValue), path: JsPath, policy: ResolutionPolicy): Either[JsError, (String, JsValue)] = {
    if (entry._1.startsWith("#")) {
      val newEntry: String = entry._1.substring(1)

      BackReferenceDeserializer.deserializeBackReference(entry._2) match {
        case JsSuccess(backReference, _) if policy.resultReferences => resolveBackReference(newEntry, backReference)
        // Resolve the value first so an invalid nested result reference still rejects the invocation.
        case _ => propagateBackReferenceResolution(entry, path, policy)
          .map(resolvedEntry => resolveCreationReferenceKey(resolvedEntry, path, policy))
      }
    } else {
      propagateBackReferenceResolution(entry, path, policy)
    }
  }

  private def resolveCreationReferenceKey(entry: (String, JsValue), path: JsPath, policy: ResolutionPolicy): (String, JsValue) = {
    val (originalKey, resolvedValue) = entry
    val shouldDeferCreationReference = !policy.resolvesKey(path, originalKey)
    if (shouldDeferCreationReference) {
      entry
    } else {
      resolveCreationId(originalKey)
        .fold(_ => entry,
          serverId => (serverId.value.value, resolvedValue))
    }
  }

  private def resolveBackReference(newEntry: String, backReference: BackReference): Either[JsError, (String, JsValue)] =
    resolve(backReference) match {
      case e: JsError => Left(e)
      case JsSuccess(resolvedBackReference, _) => Right((newEntry, resolvedBackReference))
    }

  private def propagateBackReferenceResolution(entry: (String, JsValue), path: JsPath, policy: ResolutionPolicy): Either[JsError, (String, JsValue)] =
    backReferenceResolver(path \ entry._1, policy).reads(entry._2) match {
      case e: JsError => Left(e)
      case JsSuccess(newValue, _) => Right((entry._1, newValue))
    }

  private def retrieveInvocation(callId: MethodCallId): Option[Invocation] = invocations.get(callId)

  private def resolve(backReference: BackReference): JsResult[JsValue] = retrieveInvocation(backReference.resultOf)
    .map(backReference.resolve)
    .getOrElse(JsError("Back reference could not be resolved"))
    .flatMap(resolved => if (resultReferenceBudget.consume(resolved)) {
      JsSuccess(resolved)
    } else {
      JsError(s"The result references of this request resolve to more than ${ProcessingContext.MAX_RESOLVED_RESULT_REFERENCES_SIZE} bytes")
    })

  private def resolveCreationId(creationId: String): Either[IllegalArgumentException, ServerId] =
    Id.validate(creationId.substring(1))
      .flatMap(id => retrieveServerId(ClientId(id))
        .toRight(new IllegalArgumentException(s"$creationId was not used in previously defined creationIds")))

  def resolveId(reference: IdOrCreationReference): Either[IllegalArgumentException, ServerId] =
    reference.creationId match {
      case Some(clientId) => retrieveServerId(clientId)
        .toRight(new IllegalArgumentException(s"no successful creation is recorded for creation ID '${clientId.value.value}'"))
      case None => Id.validate(reference.value).map(ServerId)
    }

  def retrieveServerId(clientId: ClientId): Option[ServerId] = creationIds.get(clientId)

}
