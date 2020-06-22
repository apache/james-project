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

import org.apache.james.server.blob.deduplication.RelatedAction.{GarbageCollect, Init, Save}
import org.apache.james.util.ClassLoaderUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.immutable.TreeSet

class GCJsonReporterTest extends AnyWordSpec with Matchers {
  "Report" should {
    val generation = Generation.first
    val blobId = GenerationAwareBlobId(generation, "myHash")
    val externalId = ExternalID("message1")

    val initialIteration = "0"
    val firstIteration = "1"
    val initialReport = JsonReport.State(Init,
      `reference-generations` = TreeSet(Generation.first),
      `garbage-collection-iterations` = TreeSet(initialIteration),
      blobs = Seq[JsonReport.BlobId](),
      references = Nil,
      dereferences = Nil)
    val firstSaveReport = JsonReport.State(Save(blobId, externalId),
      `reference-generations` = TreeSet(generation),
      `garbage-collection-iterations` = TreeSet(initialIteration),
      blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
      references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
      dereferences = Nil)
    val firstDeleteReport = JsonReport.State(RelatedAction.Dereference(externalId),
      `reference-generations` = TreeSet(generation),
      `garbage-collection-iterations` = TreeSet(initialIteration),
      blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
      references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
      dereferences = Seq(JsonReport.Dereference(blobId.asString(), generation, initialIteration)))

    val iterationForImmediateGC = Iteration(1L, Set(), generation)
    val gcReportImmediate = GCIterationReport(iterationForImmediateGC, Set())

    "be minimal" when {
      "on initial state" in {
        GCJsonReporter
          .report(GCIterationEvent(gcReportImmediate) :: Nil)
          .states should be(
          Seq(
            initialReport,
            JsonReport.State(
              GarbageCollect,
              `reference-generations` = TreeSet(Generation.first),
              `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
              blobs = Seq[JsonReport.BlobId](),
              references = Nil,
              dereferences = Nil)))
      }
    }

    "report with added references" when {
      "one reference is added" in {
        GCJsonReporter
          .report(ReferenceEvent(Reference(externalId, blobId, generation)) :: GCIterationEvent(gcReportImmediate) :: Nil)
          .states should be(Seq(
          initialReport,
          firstSaveReport,
          JsonReport.State(GarbageCollect,
            `reference-generations` = TreeSet(generation),
            `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
            blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
            references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
            dereferences = Nil)))
      }

      "one reference is added then removed" in {
        val reference = Reference(externalId, blobId, generation)
        GCJsonReporter.report(ReferenceEvent(reference) :: DereferenceEvent(Dereference(generation, reference)) :: GCIterationEvent(gcReportImmediate) :: Nil)
          .states should be(Seq(
          initialReport,
          firstSaveReport,
          firstDeleteReport,
          JsonReport.State(GarbageCollect,
            `reference-generations` = TreeSet(generation),
            `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
            blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
            references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
            dereferences = Seq(JsonReport.Dereference(blobId.asString(), generation, initialIteration)))))
      }
    }

    "GC has been ran" when {
      "report added and removed references" when {
        "one reference is added then removed and the GC is run 2 generations later" in {
          val reference = Reference(externalId, blobId, generation)
          val gcReportGenNPlus2 = GC.plan(StabilizedState(Map(generation -> List(reference)), Map(generation -> List(Dereference(generation, reference)))),
            lastIteration = Iteration.initial,
            targetedGeneration = generation.next(2))

          GCJsonReporter.report(ReferenceEvent(reference) :: DereferenceEvent(Dereference(generation, reference)) :: GCIterationEvent(gcReportGenNPlus2) :: Nil)
            .states should be(Seq(
            initialReport,
            firstSaveReport,
            firstDeleteReport,
            JsonReport.State(GarbageCollect,
              `reference-generations` = TreeSet(generation),
              `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
              blobs = Nil,
              references = Nil,
              dereferences = Nil)))
        }

        "one reference is added, a gc run two generations later, then it is removed and the GC is run again" in {
          val reference = Reference(externalId, blobId, generation)
          val gcReportGenNPlus2 = GC.plan(StabilizedState(Map(generation -> List(reference)), Map.empty),
            lastIteration = Iteration.initial,
            targetedGeneration = generation.next(2))

          val generationPlusOne= generation.next
          val dereference = Dereference(generation.next, reference)
          val gcReportGenNPlus3 = GC.plan(StabilizedState(Map(generation -> List(reference)), Map(generationPlusOne -> List(dereference))),
            lastIteration = gcReportGenNPlus2.iteration,
            targetedGeneration = generationPlusOne.next(2))

          GCJsonReporter.report(ReferenceEvent(reference) :: GCIterationEvent(gcReportGenNPlus2) :: DereferenceEvent(dereference) :: GCIterationEvent(gcReportGenNPlus3) :: Nil)
            .states should be(Seq(
            initialReport,
            firstSaveReport,
            //first gc
            JsonReport.State(GarbageCollect,
              `reference-generations` = TreeSet(generation),
              `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
              blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
              references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
              dereferences = Nil),
            //delete
            JsonReport.State(RelatedAction.Dereference(externalId),
              `reference-generations` = TreeSet(generation, generationPlusOne),
              `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration),
              blobs = Seq[JsonReport.BlobId](JsonReport.BlobId(blobId.asString, blobId.generation)),
              references = Seq(JsonReport.Reference(externalId.id, blobId.asString, generation)),
              dereferences = Seq(JsonReport.Dereference(blobId.asString(), generationPlusOne, gcReportGenNPlus2.iteration.asString))),
            //second gc
            JsonReport.State(GarbageCollect,
              `reference-generations` = TreeSet(generation, generationPlusOne),
              `garbage-collection-iterations` = TreeSet(initialIteration, firstIteration, gcReportGenNPlus3.iteration.asString),
              blobs = Nil,
              references = Nil,
              dereferences = Nil)))
        }


        "json serialization" in {
          val reference = Reference(externalId, blobId, generation)
          val gcReportGenNPlus2 = GC.plan(StabilizedState(Map(generation -> List(reference)), Map.empty),
            lastIteration = Iteration.initial,
            targetedGeneration = generation.next(2))

          val generationPlusOne = generation.next
          val dereference = Dereference(generation.next, reference)
          val gcReportGenNPlus3 = GC.plan(StabilizedState(Map(generation -> List(reference)), Map(generationPlusOne -> List(dereference))),
            lastIteration = gcReportGenNPlus2.iteration,
            targetedGeneration = generationPlusOne.next(2))

          import JsonReport._

          val actualJson = Json.toJson(GCJsonReporter.report(ReferenceEvent(reference) :: GCIterationEvent(gcReportGenNPlus2) :: DereferenceEvent(dereference) :: GCIterationEvent(gcReportGenNPlus3) :: Nil))

          actualJson should equal(Json.parse(ClassLoaderUtils.getSystemResourceAsString("gcReport.json")))
        }
      }
    }
  }
}
