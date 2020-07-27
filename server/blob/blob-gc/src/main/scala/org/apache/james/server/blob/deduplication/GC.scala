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
package org.apache.james.server.blob.deduplication

import org.apache.james.blob.api.BlobId

/**
 * Isolating and grouping Events
 */
sealed abstract class Generation extends Comparable[Generation] {
  def previous: Generation
  def previous(times: Long): Generation =
    (0L until times).foldLeft(this)((generation, _) => generation.previous)

  def next: Generation
  def next(times: Long): Generation =
    (0L until times).foldLeft(this)((generation, _) => generation.next)

  /**
   * List all generations the GC is able to collect
   */
  def collectibles(targetedGeneration: Generation): Set[Generation] =
    Generation.range(this, targetedGeneration.previous(GC.temporization)).toSet

  def <(that: Generation): Boolean = compareTo(that) < 0
  def <=(that: Generation): Boolean = compareTo(that) <= 0
  def >(that: Generation): Boolean = compareTo(that) > 0
  def >=(that: Generation): Boolean = compareTo(that) >= 0

  def asString: String
}

object Generation {
  val first: Generation = apply(0)

  def apply(id: Long): Generation = {
    if (id < 0) {
      NonExistingGeneration
    } else {
      ValidGeneration(id)
    }
  }

  def range(start: Generation, end: Generation): Seq[Generation] = (start, end) match {
    case (NonExistingGeneration, NonExistingGeneration) => Seq(NonExistingGeneration)
    case (ValidGeneration(_), NonExistingGeneration) => Nil
    case (NonExistingGeneration, ValidGeneration(id)) =>  NonExistingGeneration +: (0L to id).map(Generation.apply)
    case (ValidGeneration(id1), ValidGeneration(id2)) => (id1 to id2).map(Generation.apply)
  }
}

/**
 * Generation which has existed
 */
case class ValidGeneration(id: Long) extends Generation {
  override def previous: Generation = Generation(id - 1)

  override def next: Generation = copy(id + 1)

  override def compareTo(t: Generation): Int = t match {
    case NonExistingGeneration => 1
    case that: ValidGeneration => id.compareTo(that.id)
  }

  override def asString: String = id.toString
}

/**
 * NullObject for the initialisation of the GC
 */
case object NonExistingGeneration extends Generation {
  override def previous: Generation = NonExistingGeneration

  override def next: Generation = Generation.first

  override def compareTo(t: Generation): Int = t match {
    case NonExistingGeneration => 0
    case _: ValidGeneration => -1
  }

  override def asString: String = "non_existing"
}

/**
 * A run of the GC regarding a Set of Generations
 */
case class Iteration(id: Long, processedGenerations: Set[Generation], lastGeneration: Generation) {
  def next(generations: Set[Generation], lastGeneration: Generation): Iteration = Iteration(id + 1, generations, lastGeneration)
  def asString = id.toString
}

object Iteration {
  def initial: Iteration = Iteration(0, Set(), NonExistingGeneration)
}

case class ExternalID(id: String)

/**
 * Modelized users' interactions related to blobs
 */
sealed trait Event {
  def blob: BlobId
  def externalId: ExternalID
  def generation: Generation
}

case class Reference(externalId: ExternalID, blobId: BlobId, generation: Generation) extends Event {
  override def blob: BlobId = blobId
}

case class Dereference(generation: Generation, reference: Reference) extends Event {
  override def blob: BlobId = reference.blob
  override def externalId: ExternalID = reference.externalId
}

object Events {
  def getLastGeneration(events: Seq[Event]): Generation = events.map(_.generation).maxOption
    .getOrElse(Generation.first)

}

case class GCIterationReport(iteration: Iteration, blobsToDelete: Set[(Generation, BlobId)])

/**
 * Accessors to the References/Dereferences made by generations
 */
case class StabilizedState(references: Map[Generation, Seq[Reference]], dereferences: Map[Generation, Seq[Dereference]]) {
  private val referencedBlobsAcrossGenerations: Map[Generation, ReferencedBlobs] = {
    val blobIds = references.keys ++ dereferences.keys
    val maxGeneration = blobIds.maxOption.getOrElse(Generation.first)
    val minGeneration = blobIds.minOption.getOrElse(Generation.first)

    val initialRefs = Generation.range(NonExistingGeneration, minGeneration.previous).map((_, ReferencedBlobs(Map()))).toMap
    Generation.range(minGeneration, maxGeneration)
      .foldLeft(initialRefs)(buildGeneration)
  }

  private def buildGeneration(refs: Map[Generation, ReferencedBlobs], generation: Generation): Map[Generation, ReferencedBlobs] = {
    val populatedRefs = references.getOrElse(generation, Set())
      .foldLeft(refs(generation.previous))((currentReferences, ref) => currentReferences.addReferences(ref.blobId))

    val expungedRefs = dereferences.getOrElse(generation, Set())
      .foldLeft(populatedRefs)((currentReferences, ref) => currentReferences.removeReferences(ref.reference.blobId))

    refs + (generation -> expungedRefs)
  }

  def referencesAt(generation: Generation): ReferencedBlobs = referencedBlobsAcrossGenerations(generation)

  type ReferenceCount = Int

  case class ReferencedBlobs(blobs: Map[BlobId, ReferenceCount]) {
    def isNotReferenced(blobId: BlobId): Boolean =
      !blobs.contains(blobId)

    def addReferences(blobId: BlobId): ReferencedBlobs =
      ReferencedBlobs(blobs.updatedWith(blobId)(oldCount => oldCount.map(count => Some(count + 1)).getOrElse(Some(1))))
    def removeReferences(blobId: BlobId): ReferencedBlobs =
      ReferencedBlobs(blobs.updatedWith(blobId)(oldCount => oldCount.map(_ - 1).filter(_ > 0)))
  }

}

object GC {
  val temporization: Long = 2
  def plan(state: StabilizedState, lastIteration: Iteration, targetedGeneration: Generation): GCIterationReport = {
    val processedGenerations = lastIteration.lastGeneration.collectibles(targetedGeneration)
    val blobsToDelete = state.dereferences
      .filter { case (generation, _) => processedGenerations.contains(generation) }
      .flatMap { case (_, dereferences) => dereferences }
      .toSet
      .filter(dereference => state.referencesAt(processedGenerations.max).isNotReferenced(dereference.reference.blobId))
      .map(dereference => (dereference.reference.generation, dereference.reference.blobId))

    GCIterationReport(lastIteration.next(processedGenerations, targetedGeneration.previous(temporization)), blobsToDelete)
  }
}
