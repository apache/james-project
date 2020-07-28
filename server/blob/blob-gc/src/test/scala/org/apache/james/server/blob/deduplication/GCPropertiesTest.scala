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

import java.nio.charset.StandardCharsets

import com.google.common.hash
import org.apache.james.server.blob.deduplication.Generators.{OnePassGCTestParameters, TestParameters}
import org.scalacheck.Prop.forAll
import org.scalacheck.Test.Parameters
import org.scalacheck.{Arbitrary, Gen, Properties, Shrink}

object Generators {

  // generate a sequence of Generations with monotonic numeric ids
  // 80% of the time, the generation id is not incremented
  // 19% of the time, the generation id is incremented by 1
  // 1% of the time, the generation id is incremented by 2
  // i.e. (0, 0, 0, 2, 3, 3, 4, 5, 5, 5, 5)
  def nextGenerationsGen(previousGeneration: Generation): Gen[Generation] =
    Gen.frequency((80, Gen.const(0L)), (19, Gen.const(1L)), (1, Gen.const(2L))).map(previousGeneration.next)

  val externalIDGen: Gen[ExternalID] = Gen.uuid.map(uuid => ExternalID(uuid.toString))

  def referenceGen(generation: Generation, hash: String): Gen[Reference] = for {
    externalId <- externalIDGen
  } yield Reference(externalId, GenerationAwareBlobId(generation, hash), generation)

  case class ReferenceAccumulator(dereferenced: Set[Reference], existing: Set[Reference]) {
    def addDeletion(dereference: Reference) = this.copy(dereferenced = dereferenced ++ Set(dereference))
    def addExisting(reference: Reference) = if (dereferenced.contains(reference)) {
      this
    } else {
      this.copy(existing = existing ++ Set(reference))
    }
  }

  object ReferenceAccumulator {
    val empty: ReferenceAccumulator = ReferenceAccumulator(Set.empty, Set.empty)
  }

  def existingReferences: Seq[Event] => Set[Reference] =
    _.foldLeft(ReferenceAccumulator.empty)((acc, event) => event match {
      case dereference: Dereference => acc.addDeletion(dereference.reference)
      case reference: Reference => acc.addExisting(reference)
    }).existing

  def dereferenceGen(previousEvents: Seq[Event], generation: Generation): Gen[Option[Dereference]] = {
    val remainingReferences: Set[Reference] = existingReferences(previousEvents)
    if (remainingReferences.isEmpty) {
      Gen.const(None)
    } else {
      Gen.oneOf(remainingReferences)
        .map(reference => Dereference(generation, reference))
        .map(Some(_))
    }
  }

  val hashGenerator: Gen[String] = Gen.alphaLowerStr.map(content => hash.Hashing.sha256().hashString(content, StandardCharsets.UTF_8).toString)

  // Generate an Event, either a Reference or a Dereference (10% of the time if there are previous Events)
  def eventGen(previousEvents: Seq[Event], contentHashes: Seq[String]): Gen[Event] =
    if (previousEvents.isEmpty) {
      for {
        hashForEvent <- Gen.oneOf(contentHashes)
        firstEvent <- referenceGen(Generation.first, hashForEvent)
      } yield firstEvent
    } else {
      def pickEvent(newReferenceEvent: Reference, dereferenceEventOption: Option[Dereference]): Gen[Event] = dereferenceEventOption match {
        case Some(dereferenceEvent) => Gen.frequency((90, newReferenceEvent), (10, dereferenceEvent))
        case None => Gen.const(newReferenceEvent)
      }

      for {
        generation <- nextGenerationsGen(previousEvents.head.generation)
        contentHashForEvent <- Gen.oneOf(contentHashes)

        newReferenceEvent <- referenceGen(generation, contentHashForEvent)
        dereferenceEvent <- dereferenceGen(previousEvents, generation)

        event <- pickEvent(newReferenceEvent, dereferenceEvent)
    } yield event
  }

  // Generates a list of Events with a ratio of hashes per event to enforce referencing the same hashes multiple times.
  def eventsGen(maxNbEvents: Int, hashesPerEventsRatio: Float): Gen[Seq[Event]] =  for {
    hashes <- generateHashes(maxNbEvents, hashesPerEventsRatio)
    // Generate iteratively events until the number of events is reached
    events <- Gen.tailRecM(Seq[Event]())(previousEvents => {
      previousEvents.size match {
        case nbEvents if nbEvents >= maxNbEvents => Gen.const(Right(previousEvents))
        case _ => eventGen(previousEvents, hashes).map(event => Left(event +: previousEvents))
      }
    })
  } yield events.reverse

  def generateHashes(maxNbEvents: Int, hashesPerEventsRatio: Float): Gen[Seq[String]] = {
    val nbHashes = Math.ceil(maxNbEvents * hashesPerEventsRatio).intValue
    for {
      contentHashes <- Gen.listOfN(nbHashes, hashGenerator)
    } yield contentHashes
  }

  case class TestParameters(events: Seq[Event], generationsToCollect: Seq[Generation])
  case class OnePassGCTestParameters(events: Seq[Event], generationToCollect: Generation)

  def testParametersGen(eventsGen: Gen[Seq[Event]]): Gen[TestParameters] = for {
    events <- eventsGen
    allGenerations = Generation.range(Generation.first, Events.getLastGeneration(events))
    generationsToCollect <- Gen.someOf(allGenerations)
  } yield TestParameters(events, generationsToCollect.sorted.toSeq)

  def onePassTestParametersGen(eventsGen: Gen[Seq[Event]]): Gen[OnePassGCTestParameters] = for {
    events <- eventsGen
    generation <- Gen.oneOf(if(events.isEmpty) Seq(Generation.first) else events.map(_.generation).toSet)
  } yield OnePassGCTestParameters(events, generation)
}

object GCPropertiesTest extends Properties("GC") {
  val maxNbEvents = 100
  val hashesPerEventsRatio = 0.2f

  // Arbitrary machinery to effective shrinking
  val arbEvents: Arbitrary[Seq[Event]] = Arbitrary(Gen.choose(0, maxNbEvents).flatMap(Generators.eventsGen(_, hashesPerEventsRatio)))
  implicit val arbTestParameters: Arbitrary[Generators.TestParameters] = Arbitrary(Generators.testParametersGen(arbEvents.arbitrary))
  implicit val arbTestParameter: Arbitrary[Generators.OnePassGCTestParameters] = Arbitrary(Generators.onePassTestParametersGen(arbEvents.arbitrary))
  import org.scalacheck.Shrink._

  override def overrideParameters(p: Parameters) =
    p.withMinSuccessfulTests(1000)

  def createSaneTestParameters(events: Seq[Event], generations: Seq[Generation]): TestParameters = {
    val allGenerations = Generation.range(Generation.first, Events.getLastGeneration(events))
    TestParameters(events, generations.filter(allGenerations.contains(_)))
  }
  def createSaneOnePassGCTestParameters(events: Seq[Event], generation: Generation): OnePassGCTestParameters = {
    OnePassGCTestParameters(events, generation)
  }

  implicit val shrinkTestParameters: Shrink[Generators.TestParameters] = Shrink {
    params: Generators.TestParameters =>
      shrink(params.events).flatMap(events => shrink(params.generationsToCollect).map(generations => createSaneTestParameters(events, generations)))
  }

  implicit val shrinkOnePassGCTestParameters: Shrink[Generators.OnePassGCTestParameters] = Shrink {
    params: Generators.OnePassGCTestParameters =>
      shrink(params.events).map(events => createSaneOnePassGCTestParameters(events, params.generationToCollect))
  }

  property("2.1. GC should not delete data being referenced by a pending process or still referenced") = forAll {
    testParameters: Generators.TestParameters => {

      val partitionedBlobsId =  Oracle.partitionBlobs(testParameters.events)
      testParameters.generationsToCollect.foldLeft(true)((acc, e) => {
        val plannedDeletions = GC.plan(Interpreter(testParameters.events).stabilize(), Iteration.initial, e).blobsToDelete.map(_._2)
        acc && partitionedBlobsId.stillReferencedBlobIds.intersect(plannedDeletions).isEmpty
      })
    }
  }

  property("3.2. less than 10% of unreferenced data of a significant dataset should persist") = forAll {
    testParameters: Generators.OnePassGCTestParameters => {
      if (testParameters.generationToCollect >= Events.getLastGeneration(testParameters.events).previous(GC.temporization))
        true
      else {
        val plan = GC.plan(Interpreter(testParameters.events).stabilize(), Iteration.initial, testParameters.generationToCollect)
        // An Event belongs to a collected Generation
        val relevantEvents: Event => Boolean = event => event.generation <= testParameters.generationToCollect.previous(GC.temporization)
        val plannedDeletions = plan.blobsToDelete.map(_._2)

        val partitionedBlobsId = Oracle.partitionBlobs(testParameters.events.filter(relevantEvents))
        plannedDeletions.size >= partitionedBlobsId.notReferencedBlobIds.size * 0.9
      }
    }
  }
}


