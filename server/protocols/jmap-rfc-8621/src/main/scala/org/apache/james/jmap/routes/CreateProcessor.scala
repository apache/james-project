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
  def process[A, E, R](creates: Map[ClientId, A], initialContext: ProcessingContext)(
                       dependencies: A => Set[ClientId],
                       create: (A, ProcessingContext) => SMono[Either[E, (R, ServerId)]],
                       invalidDependency: A => E): SMono[(Map[ClientId, Either[E, R]], ProcessingContext)] =
    SMono.defer(() => {
      val localDependencies = creates.view.mapValues(value => dependencies(value).intersect(creates.keySet)).toMap
      val ordered = order(localDependencies)
      // Emit one accumulated state per creation. expand avoids a recursive flatMap
      // subscription chain when many synchronous creates depend on one another.
      SMono.just((ordered.toList, Map.empty[ClientId, Either[E, R]], initialContext))
        .expand {
          case (Nil, _, _) => SMono.empty
          case (id :: remaining, results, context) =>
            processCreation(id, creates(id), localDependencies(id), results, context)(create, invalidDependency)
              .map { case (updatedResults, updatedContext) => (remaining, updatedResults, updatedContext) }
        }
        .last()
        .map { case (_, results, context) => (results, context) }
    })

  private def processCreation[A, E, R](id: ClientId, entry: A, dependencies: Set[ClientId],
                                      results: Map[ClientId, Either[E, R]], context: ProcessingContext)(
                                      create: (A, ProcessingContext) => SMono[Either[E, (R, ServerId)]],
                                      invalidDependency: A => E): SMono[(Map[ClientId, Either[E, R]], ProcessingContext)] =
    if (dependencies.exists(dependency => !results.get(dependency).exists(_.isRight))) {
      SMono.just((results.updated(id, Left(invalidDependency(entry))), context))
    } else {
      create(entry, context).map {
        case Left(error) => (results.updated(id, Left(error)), context)
        case Right((result, serverId)) =>
          (results.updated(id, Right(result)), context.recordCreatedId(id, serverId))
      }
    }

  // Kahn's algorithm: each vertex and edge is visited once. Remaining vertices
  // belong to a cycle or depend on one; process() rejects them without side effects.
  private def order(dependencies: Map[ClientId, Set[ClientId]]): Seq[ClientId] = {
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
