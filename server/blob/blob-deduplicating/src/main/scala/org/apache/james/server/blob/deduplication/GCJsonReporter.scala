/***************************************************************
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
package org.apache.james.server.blob.deduplication

import org.apache.james.blob.api.BlobId
import org.apache.james.server.blob.deduplication.RelatedAction.{Delete, GarbageCollect, Init, Save}
import play.api.libs.json.{JsString, Json, Writes}

import scala.collection.immutable.TreeSet


sealed trait RelatedAction
object RelatedAction {
  case object Init extends RelatedAction
  case class Save(blobId: BlobId, reference: ExternalID) extends RelatedAction
  case class Delete(reference: ExternalID) extends RelatedAction
  case object GarbageCollect extends RelatedAction
}

object JsonReport {
  case class BlobId(id : String, `reference-generation`: Generation)

  case class Reference(id : String, blob: String, `reference-generation`: Generation)
  case class Dereference(blob: String, `reference-generation`: Generation, `garbage-collection-iterations`: String)

  case class State(`related-action` : RelatedAction,
                   `reference-generations`: TreeSet[Generation],
                   `garbage-collection-iterations`: TreeSet[String],
                   blobs: Seq[BlobId],
                   references: Seq[Reference],
                   deletions: Seq[Dereference])


  //action
  implicit val relatedActionWrites: Writes[RelatedAction] = {
    case Init => JsString("init")
    case Save(blobId, reference) => JsString(s"save(blob = ${blobId.asString()}, reference = ${reference.id})")
    case Delete(reference) => JsString(s"delete(reference = ${reference.id})")
    case GarbageCollect => JsString(s"garbageCollect")
  }
  //generation
  implicit val generationWrites: Writes[Generation] = {
    case ValidGeneration(id) => JsString(s"$id")
    case NonExistingGeneration => JsString(s"nonExistingGen")
  }
  //blobid
  implicit val blobIdWrites: Writes[BlobId] = Json.writes[BlobId]
  //reference
  implicit val referenceWrites: Writes[Reference] = Json.writes[Reference]
  //dereference
  implicit val dereferenceWrites: Writes[Dereference] = Json.writes[Dereference]
  //JsonReport.State
  implicit val stateWrites: Writes[State] = Json.writes[State]
  //JsonReport
  implicit val reportWrites: Writes[JsonReport] = Json.writes[JsonReport]

}

case class JsonReport(states: Seq[JsonReport.State])


sealed trait EventToReport

case class ReferenceEvent(event : Reference) extends EventToReport
case class DereferenceEvent(event : Dereference) extends EventToReport
case class GCIterationEvent(event : GCIterationReport) extends EventToReport

object EventToReport {
  def extractReferencingEvents(events: Seq[EventToReport]): Seq[Event] =
    events.flatMap {
      case ReferenceEvent(reference) => Some(reference)
      case DereferenceEvent(dereference) => Some(dereference)
      case GCIterationEvent(_) => None
    }
  def toReportEvents(events: Seq[Event]): Seq[EventToReport] =
    events.map {
      case reference: Reference => ReferenceEvent(reference)
      case dereference: Dereference => DereferenceEvent(dereference)
    }
}

object GCJsonReporter {

  def report(events: Seq[EventToReport]) : JsonReport = {

   events.foldLeft((Seq[EventToReport](), JsonReport(Seq(JsonReport.State(Init,
      TreeSet(Generation.first),
      TreeSet(Iteration.initial.asString),
      Seq[JsonReport.BlobId](),
      Nil,
      Nil)))))((acc, event) => {
      val (events, reportStates) = acc
      val currentEvents = events :+ event

      val state : JsonReport.State = event match {
        case ReferenceEvent(reference) =>
          stateForReference(reportStates, reference)
        case DereferenceEvent(dereference) =>
          stateForDereference(reportStates, dereference)
        case GCIterationEvent(gcReports) =>
          val curatedAcc = (EventToReport.extractReferencingEvents(acc._1), acc._2)
          stateForGCIteration(curatedAcc, EventToReport.extractReferencingEvents(events), gcReports)
      }

      (currentEvents, JsonReport(reportStates.states :+ state))
    })._2


  }

  private def stateForGCIteration(acc: (Seq[Event], JsonReport), events: Seq[Event], gcReports: GCIterationReport) = {
    val lastState = acc._2.states.last

    val blobsToDeleteAsString = gcReports.blobsToDelete.map(_._2).map(_.asString())

    JsonReport.State(GarbageCollect,
      `reference-generations` = lastState.`reference-generations`,
      `garbage-collection-iterations` = lastState.`garbage-collection-iterations` + gcReports.iteration.asString,
      blobs = lastState.blobs.diff(gcReports.blobsToDelete.map { case (generation, blobId) => JsonReport.BlobId(blobId.asString, generation) }.toSeq),
      references = lastState.references.filterNot(reference => blobsToDeleteAsString.contains(reference.blob)),
      deletions = lastState.deletions.filterNot(dereference => blobsToDeleteAsString.contains(dereference.blob)))
  }

  private def stateForDereference(reportStates: JsonReport, dereference: Dereference) = {
    val previousState = reportStates.states.last
    val referenceGenerations = previousState.`reference-generations` + dereference.generation
    val iterations = previousState.`garbage-collection-iterations`
    val references = previousState.references
    val lastIteration = previousState.`garbage-collection-iterations`.last
    val dereferences = previousState.deletions :+ JsonReport.Dereference(dereference.blob.asString(), dereference.generation, lastIteration)

    JsonReport.State(Delete(dereference.externalId),
      `reference-generations` = referenceGenerations,
      `garbage-collection-iterations` = iterations,
      blobs = previousState.blobs,
      references = references,
      deletions = dereferences)
  }

  private def stateForReference(reportStates: JsonReport, add: Reference) = {
    val previousState = reportStates.states.last
    val referenceGenerations = previousState.`reference-generations` + add.generation
    val iterations = previousState.`garbage-collection-iterations`
    val blobId = JsonReport.BlobId(add.blobId.asString(), add.generation)
    val blobs = if (previousState.blobs.contains(blobId))
      previousState.blobs
    else
      previousState.blobs :+ JsonReport.BlobId(add.blobId.asString(), add.generation)
    val references = previousState.references :+ JsonReport.Reference(add.externalId.id, add.blobId.asString(), add.generation)
    val deletions = previousState.deletions

    JsonReport.State(Save(add.blobId, add.externalId), referenceGenerations, iterations, blobs, references, deletions)
  }
}
