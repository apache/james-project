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
                                     invalidDependency: Input => Error): SMono[(Map[ClientId, Either[Error, Created]], ProcessingContext)] =
    SMono.defer(() => {
      val dependenciesInThisCall = creates.view.mapValues(value => dependencies(value).intersect(creates.keySet)).toMap
      val creationOrder = orderByDependencies(dependenciesInThisCall)
      // Emit one accumulated state per creation. expand avoids a recursive flatMap
      // subscription chain when many synchronous creates depend on one another.
      val initialState = ProcessingState[Error, Created](
        remaining = creationOrder.toList,
        results = Map.empty,
        context = initialContext)
      SMono.just(initialState)
        .expand(state => processNextCreation(state, creates, dependenciesInThisCall)(create, invalidDependency))
        .last()
        .map(state => (state.results, state.context))
    })

  private def processNextCreation[Input, Error, Created](state: ProcessingState[Error, Created],
                                                         creates: Map[ClientId, Input],
                                                         dependenciesInThisCall: Map[ClientId, Set[ClientId]])(
                                                         create: (Input, ProcessingContext) => SMono[Either[Error, (Created, ServerId)]],
                                                         invalidDependency: Input => Error): SMono[ProcessingState[Error, Created]] =
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
                                                     invalidDependency: Input => Error): SMono[(Map[ClientId, Either[Error, Created]], ProcessingContext)] = {
    // A failed or cyclic same-call dependency must not fall back to an older mapping.
    val hasUnsuccessfulDependency = dependencies.exists(dependency => !results.get(dependency).exists(_.isRight))
    if (hasUnsuccessfulDependency) {
      SMono.just((results.updated(id, Left(invalidDependency(entry))), context))
    } else {
      create(entry, context).map {
        case Left(error) => (results.updated(id, Left(error)), context)
        case Right((result, serverId)) =>
          (results.updated(id, Right(result)), context.recordCreatedId(id, serverId))
      }
    }
  }

  // Use Kahn's algorithm to process parents before their dependents. Keep entries
  // blocked by cycles so processCreation can reject them without attempting creation.
  private def orderByDependencies(dependencies: Map[ClientId, Set[ClientId]]): Seq[ClientId] = {
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
    ordered.result() ++ unresolvedParentCounts.keys
  }
}
