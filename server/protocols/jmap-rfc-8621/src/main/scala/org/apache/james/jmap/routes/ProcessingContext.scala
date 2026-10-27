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

import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.NonNegInt
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId}
import org.apache.james.jmap.json.BackReferenceDeserializer
import play.api.libs.json.{JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Reads}

import scala.collection.IndexedSeq
import scala.collection.Map
import scala.collection.immutable.{Map => ImmutableMap}
import scala.util.Try

sealed trait JsonPathPart

case object WildcardPart extends JsonPathPart

case class PlainPart(name: String) extends JsonPathPart {
  def read(jsValue: JsValue): JsResult[JsValue] = jsValue match {
    case JsObject(underlying) => underlying.get(name).map(JsSuccess(_)).getOrElse(JsError(s"Expected path $name was missing"))
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
      case part: String => ArrayElementPart.parse(part)
        .orElse(Some(PlainPart(part)))
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

case class InvalidResultReferenceException(message: String) extends IllegalArgumentException

case class ProcessingContext(private val creationIds: ImmutableMap[ClientId, ServerId],
                             private val invocations: ImmutableMap[MethodCallId, Invocation]) {

  def recordCreatedId(clientId: ClientId, serverId: ServerId): ProcessingContext = ProcessingContext(creationIds + (clientId -> serverId), invocations)

  def recordInvocation(invocation: Invocation): ProcessingContext = ProcessingContext(creationIds, invocations + (invocation.methodCallId -> invocation))

  def resolveBackReferences(invocation: Invocation): Either[InvalidResultReferenceException, Invocation] =
    backReferenceResolver.reads(invocation.arguments.value) match {
      case JsError(e) => Left(InvalidResultReferenceException(e.toString()))
      case JsSuccess(JsObject(underlying), _) => Right(Invocation(methodName = invocation.methodName,
        methodCallId = invocation.methodCallId,
        arguments = Arguments(JsObject(underlying))))
      case others: JsSuccess[JsValue] => Left(InvalidResultReferenceException(s"Unexpected value $others"))
    }

  private val backReferenceResolver: Reads[JsValue] = {
    case JsArray(value) => resolveBackReferences(value)
    case JsObject(underlying) => resolveBackReference(underlying)
    case JsString(value) if value.startsWith("#") => resolveCreationId(value)
        .fold(_ => JsSuccess(JsString(value)),
          serverId => JsSuccess(JsString(serverId.value.value)))
    case others: JsValue => JsSuccess(others)
  }

  private def resolveBackReferences(array: IndexedSeq[JsValue]): JsResult[JsValue] = {
    val results: IndexedSeq[JsResult[JsValue]] = array.map(backReferenceResolver.reads)
    results.find(_.isError)
      .getOrElse(JsSuccess(JsArray(results.map(_.get))))
  }

  private def resolveBackReference(underlying: Map[String, JsValue]): JsResult[JsObject] = {
    val resolutions = underlying.map(resolveBackReference)

    val firstError = resolutions.flatMap(_.left.toOption).headOption

    val transformedMap = resolutions.flatMap(_.toOption).toMap

    firstError.getOrElse(JsSuccess(JsObject(transformedMap)))
  }

  private def resolveBackReference(entry: (String, JsValue)): Either[JsError, (String, JsValue)] = {
    if (entry._1.startsWith("#")) {
      val newEntry: String = entry._1.substring(1)

      BackReferenceDeserializer.deserializeBackReference(entry._2) match {
        case JsSuccess(backReference, _) => resolveBackReference(newEntry, backReference)
        // If the JSON object is not a back-reference continue parsing (it could be a creationId)
        case _: JsError =>
          backReferenceResolver.reads(entry._2)
            .fold(e => Left(JsError(e)),
              json => resolveCreationId(entry._1)
                .fold(_ => Right((entry._1, json)),
                  serverId => Right((serverId.value.value, json))))
      }
    } else {
      propagateBackReferenceResolution(entry)
    }
  }

  private def resolveBackReference(newEntry: String, backReference: BackReference): Either[JsError, (String, JsValue)] =
    resolve(backReference) match {
      case e: JsError => Left(e)
      case JsSuccess(resolvedBackReference, _) => Right((newEntry, resolvedBackReference))
    }

  private def propagateBackReferenceResolution(entry: (String, JsValue)): Either[JsError, (String, JsValue)] =
    backReferenceResolver.reads(entry._2) match {
      case e: JsError => Left(e)
      case JsSuccess(newValue, _) => Right((entry._1, newValue))
    }

  private def retrieveInvocation(callId: MethodCallId): Option[Invocation] = invocations.get(callId)

  private def resolve(backReference: BackReference): JsResult[JsValue] = retrieveInvocation(backReference.resultOf)
    .map(backReference.resolve)
    .getOrElse(JsError("Back reference could not be resolved"))

  private def resolveCreationId(creationId: String): Either[IllegalArgumentException, ServerId] =
    Id.validate(creationId)
      .flatMap(id => resolveServerId(ClientId(id)))

 private def resolveServerId(id: ClientId): Either[IllegalArgumentException, ServerId] =
  id.retrieveOriginalClientId
    .map(maybePreviousClientId => maybePreviousClientId.flatMap(previousClientId => retrieveServerId(previousClientId)
      .map(Right(_))
      .getOrElse(Left[IllegalArgumentException, ServerId](new IllegalArgumentException(s"$id was not used in previously defined creationIds")))))
    .getOrElse(Right(ServerId(id.value)))

  private def retrieveServerId(clientId: ClientId): Option[ServerId] = creationIds.get(clientId)

}
