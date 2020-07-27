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

case class GenerationAwareBlobId(generation: Generation, hash: String) extends BlobId {
  override def asString(): String = s"${generation.asString}_$hash"
}

case class PartitionedEvents(stillReferencedBlobIds: Set[BlobId], notReferencedBlobIds: Set[BlobId])

/**
 * Used to iteratively build a StabilizedState
 */
case class State(references: Map[Generation, Seq[Reference]], deletions: Map[Generation, Seq[Dereference]]) {

  def stabilize(): StabilizedState = StabilizedState(references, deletions)

  def apply(event: Event): State = event match {
    case e: Reference => copy(references = addElement(references, e))
    case e: Dereference => copy(deletions = addElement(deletions, e))
  }

  private def addElement[T <: Event](collection: Map[Generation, Seq[T]], e: T): Map[Generation, Seq[T]] = {
    collection.updatedWith(e.generation)(previous => Some(e +: previous.getOrElse(Seq())))
  }
}

object State {
  val initial: State = State(references = Map.empty, deletions = Map.empty)
}

object Interpreter {
  def apply(events: Seq[Event]): State =
    events.foldLeft(State.initial)((state, event) => state(event))
}

object Oracle {
  /*
  Implement an oracle that implements BlobStore with a Ref Count reference tracking
   */
  def partitionBlobs(events: Seq[Event]): PartitionedEvents = {
    val (referencingEvents, dereferencingEvents) = events.partition {
      case _: Reference => true
      case _: Dereference => false
    }

    val referencedBlobsCount = referencingEvents.groupBy(_.blob).view.mapValues(_.size).toMap
    val dereferencedBlobsCount = dereferencingEvents.groupBy(_.blob).view.mapValues(_.size).toMap

    val stillReferencedBlobIds = referencedBlobsCount.foldLeft(Set[BlobId]())((acc, kv) => {
      val (blobId, referencesCount) = kv
      val dereferencesCount  = dereferencedBlobsCount.getOrElse(blobId, 0)

      if(referencesCount > dereferencesCount)
        acc + blobId
      else
        acc
    })

    lazy val notReferencedBlobIds = dereferencedBlobsCount.keySet -- stillReferencedBlobIds
    PartitionedEvents(stillReferencedBlobIds, notReferencedBlobIds)
  }
}

