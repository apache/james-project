/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.routes

import org.apache.james.jmap.core.{ClientId, ServerId}
import reactor.core.scala.publisher.SMono

import scala.collection.mutable

/** Dependencies must describe only references to records of the type being created.
  * Foreign keys to other types share the context, but are not ordering edges.
  */
object CreateProcessor {
  sealed trait DependencyFailure
  case class FailedCreation(id: ClientId) extends DependencyFailure
  case object BlockedByCycle extends DependencyFailure

  private case class ProcessingState[Error, Created](remaining: List[ClientId],
                                                     results: Map[ClientId, Either[Error, Created]],
                                                     context: ProcessingContext)

  /** Order same-call dependencies, then create each item with the context accumulated so far.
    * Only successful creations add a server ID to the context.
    * The create callback returns the created value and its server ID, or an item error.
    * References outside this call are resolved by the consumer using that context.
    */
  def process[Input, Error, Created](creates: Map[ClientId, Input], initialContext: ProcessingContext)(
                                     dependencies: Input => Set[ClientId],
                                     create: (Input, ProcessingContext) => SMono[Either[Error, (Created, ServerId)]],
                                     invalidDependency: DependencyFailure => Error): SMono[(Map[ClientId, Either[Error, Created]], ProcessingContext)] =
    SMono.defer(() => {
      val dependenciesInThisCall = creates.view.mapValues(value => dependencies(value).intersect(creates.keySet)).toMap
      val (creationOrder, blockedByCycles) = orderByDependencies(dependenciesInThisCall)
      // Emit one accumulated state per creation. expand avoids a recursive flatMap
      // subscription chain when many synchronous creates depend on one another.
      val initialState = ProcessingState[Error, Created](
        remaining = creationOrder.toList,
        results = Map.empty,
        context = initialContext)
      SMono.just(initialState)
        .expand(state => processNextCreation(state, creates, dependenciesInThisCall)(create, invalidDependency))
        .last()
        .map(state => {
          val blockedResults = blockedByCycles.iterator.map(id => id -> Left(invalidDependency(BlockedByCycle)))
          (state.results ++ blockedResults, state.context)
        })
    })

  private def processNextCreation[Input, Error, Created](state: ProcessingState[Error, Created],
                                                         creates: Map[ClientId, Input],
                                                         dependenciesInThisCall: Map[ClientId, Set[ClientId]])(
                                                         create: (Input, ProcessingContext) => SMono[Either[Error, (Created, ServerId)]],
                                                         invalidDependency: DependencyFailure => Error): SMono[ProcessingState[Error, Created]] =
    state.remaining match {
      case Nil => SMono.empty
      case creationId :: remaining =>
        processCreation(creationId, creates(creationId), dependenciesInThisCall(creationId), state.results, state.context)(create, invalidDependency)
          .map { case (results, context) =>
            ProcessingState(remaining = remaining, results = results, context = context)
          }
    }

  private def processCreation[Input, Error, Created](id: ClientId, entry: Input, dependencies: Set[ClientId],
                                                     results: Map[ClientId, Either[Error, Created]], context: ProcessingContext)(
                                                     create: (Input, ProcessingContext) => SMono[Either[Error, (Created, ServerId)]],
                                                     invalidDependency: DependencyFailure => Error): SMono[(Map[ClientId, Either[Error, Created]], ProcessingContext)] = {
    // The dependency has already been attempted. If it failed, an earlier successful
    // mapping can still resolve the reference.
    val unresolvedDependency = dependencies.find(dependency => context.retrieveServerId(dependency).isEmpty)
    unresolvedDependency match {
      case Some(dependency) =>
        SMono.just((results.updated(id, Left(invalidDependency(FailedCreation(dependency)))), context))
      case None =>
        create(entry, context).map {
          case Left(error) => (results.updated(id, Left(error)), context)
          case Right((result, serverId)) =>
            (results.updated(id, Right(result)), context.recordCreatedId(id, serverId))
        }
    }
  }

  // Use Kahn's algorithm to order parents before their dependents and identify
  // entries that cannot be ordered because of a cycle.
  private def orderByDependencies(dependencies: Map[ClientId, Set[ClientId]]): (Seq[ClientId], Set[ClientId]) = {
    val unresolvedParentCounts = mutable.Map.from(dependencies.view.mapValues(_.size))
    val dependents = mutable.Map.empty[ClientId, Vector[ClientId]]
    dependencies.foreach { case (id, parents) =>
      parents.foreach(parent => dependents.update(parent, dependents.getOrElse(parent, Vector.empty) :+ id))
    }
    val ready = mutable.Queue.from(dependencies.keys.filter(id => unresolvedParentCounts(id) == 0))
    val ordered = Vector.newBuilder[ClientId]
    while (ready.nonEmpty) {
      val id = ready.dequeue()
      ordered += id
      unresolvedParentCounts.remove(id)
      dependents.getOrElse(id, Vector.empty).foreach(child => {
        unresolvedParentCounts.update(child, unresolvedParentCounts(child) - 1)
        if (unresolvedParentCounts(child) == 0) {
          ready.enqueue(child)
        }
      })
    }
    (ordered.result(), unresolvedParentCounts.keySet.toSet)
  }
}
