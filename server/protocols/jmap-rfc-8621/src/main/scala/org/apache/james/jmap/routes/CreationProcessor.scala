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

import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Properties, ServerId, SetError}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.collection.mutable

case class CreationResults[C, R](created: Map[C, R], notCreated: Map[C, SetError], processingContext: ProcessingContext)

private[routes] object CreationProcessor {
  private def invalidReference(property: String, description: String): SetError =
    SetError.invalidProperties(SetErrorDescription(description), Some(Properties.toProperties(Set(property))))

  private def references(value: JsValue): Seq[String] = value match {
    case JsString(id) if id.startsWith("#") => Seq(id)
    case JsObject(fields) => fields.toSeq.flatMap { case (key, nested) =>
      (if (key.startsWith("#")) Seq(key) else Seq.empty) ++ references(nested)
    }
    case JsArray(values) => values.flatMap(references).toSeq
    case _ => Seq.empty
  }

  private def dependencies(json: JsObject, properties: Set[String]): Set[ClientId] =
    properties.toSeq.flatMap(property => (json \ property).toOption.toSeq.flatMap(references))
      .flatMap(reference => Id.validate(reference.substring(1)).toOption.map(ClientId)).toSet

  def resolveReferences(json: JsObject, properties: Set[String], context: ProcessingContext): Either[SetError, JsObject] = {
    def resolveId(id: String, property: String): Either[SetError, String] =
      if (!id.startsWith("#")) {
        Right(id)
      } else {
        Id.validate(id.substring(1)).toOption.flatMap(value => context.retrieveServerId(ClientId(value)))
          .map(_.value.value).toRight(invalidReference(property, s"Unable to resolve $property: $id"))
      }

    def resolve(value: JsValue, property: String): Either[SetError, JsValue] = value match {
      case JsString(id) => resolveId(id, property).map(JsString)
      case JsObject(fields) => fields.toSeq.foldLeft[Either[SetError, JsObject]](Right(JsObject.empty)) {
        case (acc, (key, nested)) => for {
          resolved <- acc
          resolvedKey <- resolveId(key, property)
          resolvedValue <- resolve(nested, property)
        } yield resolved + (resolvedKey -> resolvedValue)
      }
      case JsArray(values) => values.foldLeft[Either[SetError, Vector[JsValue]]](Right(Vector.empty)) {
        case (acc, nested) => for {
          resolved <- acc
          resolvedValue <- resolve(nested, property)
        } yield resolved :+ resolvedValue
      }.map(JsArray)
      case other => Right(other)
    }

    properties.foldLeft[Either[SetError, JsObject]](Right(json)) { (acc, property) =>
      acc.flatMap(current => (current \ property).toOption
        .map(value => resolve(value, property).map(resolved => current + (property -> resolved)))
        .getOrElse(Right(current)))
    }
  }

  def process[C, R](context: ProcessingContext,
                    creations: Map[C, JsObject],
                    properties: Set[String],
                    validate: JsObject => Either[SetError, Unit])
                   (clientId: C => Id.Id, serverId: R => String)
                   (create: (C, JsObject) => SMono[Either[SetError, R]]): SMono[CreationResults[C, R]] = SMono.defer(() => {
    val ids = creations.keysIterator.map(id => ClientId(clientId(id)) -> id).toMap
    val validations = creations.map { case (id, json) => id -> validate(json) }
    val parents = creations.map { case (id, json) => id -> validations(id).toOption
      .map(_ => dependencies(json, properties).flatMap(ids.get)).getOrElse(Set.empty[C]) }
    val children = parents.toSeq.flatMap { case (child, dependencies) => dependencies.map(_ -> child) }.groupMap(_._1)(_._2)
    val waiting = mutable.Map.from(parents.map { case (id, dependencies) => id -> dependencies.size })
    val ready = mutable.Queue.from(waiting.keysIterator.filter(id => waiting(id) == 0))
    val ordered = Vector.newBuilder[C]
    while (ready.nonEmpty) {
      val id = ready.dequeue()
      ordered += id
      waiting.remove(id)
      children.getOrElse(id, Seq.empty).foreach { child =>
        waiting.update(child, waiting(child) - 1)
        if (waiting(child) == 0) {
          ready.enqueue(child)
        }
      }
    }
    val cycles = waiting.keysIterator.map { id =>
      val property = properties.find(property => (creations(id) \ property).toOption.exists(value => references(value).nonEmpty)).get
      id -> invalidReference(property, "Cyclic creation dependency")
    }.toMap
    var result = CreationResults[C, R](Map.empty, cycles, context)
    SFlux.fromIterable(ordered.result())
      .concatMap(id => validations(id).flatMap(_ => resolveReferences(creations(id), properties, result.processingContext))
        .fold(error => SMono.just[Either[SetError, R]](Left(error)), json => create(id, json))
        .map(_.flatMap(response => Id.validate(serverId(response))
          .left.map(error => SetError.serverFail(SetErrorDescription(error.getMessage)))
          .map(id => response -> ServerId(id))))
        .doOnNext {
          case Right((response, assignedId)) => result = result.copy(
            created = result.created + (id -> response),
            processingContext = result.processingContext.recordCreatedId(ClientId(clientId(id)), assignedId))
          case Left(error) => result = result.copy(notCreated = result.notCreated + (id -> error))
        })
      .collectSeq()
      .map(_ => result)
  })
}
