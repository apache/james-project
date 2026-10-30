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

import java.util.concurrent.atomic.AtomicInteger

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.{ClientId, Id, ServerId}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

import scala.collection.immutable.ListMap

class CreateProcessorTest {
  private val emptyContext = ProcessingContext(Map.empty, Map.empty)
  private val parent = ClientId("parent")
  private val child = ClientId("child")
  private val grandchild = ClientId("grandchild")

  private def serverId(id: ClientId): ServerId = ServerId(Id.validate("server-" + id.value.value).toOption.get)

  private def process(entries: Seq[(ClientId, Set[ClientId])], context: ProcessingContext = emptyContext,
                      failures: Set[ClientId] = Set.empty, executions: AtomicInteger = new AtomicInteger()) =
    CreateProcessor.process(ListMap(entries.map { case (id, parents) => id -> (id, parents) }: _*), context)(
      dependencies = (entry: (ClientId, Set[ClientId])) => entry._2,
      create = (entry: (ClientId, Set[ClientId]), current: ProcessingContext) => SMono.fromCallable(() => {
        executions.incrementAndGet()
        val (id, parents) = entry
        if (failures.contains(id)) {
          Left("domain failure")
        } else {
          val resolvedParents = parents.map(p => current.retrieveServerId(p).get)
          Right((resolvedParents, serverId(id)))
        }
      }),
      invalidDependency = (_: (ClientId, Set[ClientId])) => "invalid dependency")

  @Test
  def shouldCreateEveryPermutationWithTheAccumulatedContext(): Unit = {
    Seq(parent -> Set.empty[ClientId], child -> Set(parent), grandchild -> Set(child)).permutations.foreach(entries => {
      val (results, context) = process(entries).block()
      assertThat(results(parent)).isEqualTo(Right(Set.empty[ServerId]))
      assertThat(results(child)).isEqualTo(Right(Set(serverId(parent))))
      assertThat(results(grandchild)).isEqualTo(Right(Set(serverId(child))))
      assertThat(context.retrieveServerId(grandchild)).isEqualTo(Some(serverId(grandchild)))
      assertThat(emptyContext.retrieveServerId(parent).isEmpty).isTrue()
    })
  }

  @Test
  def shouldRejectCyclesAndTheirDescendantsWhileCreatingIndependentItems(): Unit = {
    val self = ClientId("self")
    val root = ClientId("root")
    val executions = new AtomicInteger()
    val (results, _) = process(Seq(parent -> Set(child), child -> Set(parent), grandchild -> Set(child),
      self -> Set(self), root -> Set.empty[ClientId]), executions = executions).block()
    Seq(parent, child, grandchild, self).foreach(id => assertThat(results(id)).isEqualTo(Left("invalid dependency")))
    assertThat(results(root)).isEqualTo(Right(Set.empty[ServerId]))
    assertThat(executions.get()).isEqualTo(1)
  }

  @Test
  def shouldKeepTheOriginalFailureAndRejectDependentsEvenWhenAnOldMappingExists(): Unit = {
    val oldContext = emptyContext.recordCreatedId(parent, ServerId("old-parent"))
    val (results, context) = process(Seq(child -> Set(parent), parent -> Set.empty[ClientId]), oldContext, Set(parent)).block()
    assertThat(results(parent)).isEqualTo(Left("domain failure"))
    assertThat(results(child)).isEqualTo(Left("invalid dependency"))
    assertThat(context).isEqualTo(oldContext)
  }

  @Test
  def shouldReplaceAnOldMappingOnlyAfterSuccessfulCreation(): Unit = {
    val oldContext = emptyContext.recordCreatedId(parent, ServerId("old-parent"))
    val (results, context) = process(Seq(child -> Set(parent), parent -> Set.empty[ClientId]), oldContext).block()
    assertThat(results(child)).isEqualTo(Right(Set(serverId(parent))))
    assertThat(context.retrieveServerId(parent)).isEqualTo(Some(serverId(parent)))
  }

  @Test
  def shouldAllowDependenciesOutsideTheCurrentCreateMap(): Unit = {
    val context = emptyContext.recordCreatedId(parent, ServerId("previous-call"))
    val (results, _) = process(Seq(child -> Set(parent)), context).block()
    assertThat(results(child)).isEqualTo(Right(Set(ServerId("previous-call"))))
  }

  @Test
  def shouldSupportMultipleDependenciesAndSharedAncestors(): Unit = {
    val (results, _) = process(Seq(grandchild -> Set(parent, child), child -> Set(parent), parent -> Set.empty[ClientId])).block()
    assertThat(results(grandchild)).isEqualTo(Right(Set(serverId(parent), serverId(child))))
  }

  @Test
  def shouldReturnAnEmptyResultWithoutChangingTheContext(): Unit = {
    val (results, context) = process(Seq.empty).block()
    assertThat(results.isEmpty).isTrue()
    assertThat(context).isEqualTo(emptyContext)
  }

  @Test
  def shouldExecuteCreationsOnlyOnceAcrossRepeatedSubscriptions(): Unit = {
    val executions = new AtomicInteger()
    val publisher = process(Seq(parent -> Set.empty[ClientId], child -> Set(parent)), executions = executions)
    assertThat(executions.get()).isZero()
    val first = publisher.block()
    assertThat(publisher.block()).isEqualTo(first)
    assertThat(executions.get()).isEqualTo(2)
  }
  @Test
  def shouldProcessALongReversedChainWithoutRecursiveTraversal(): Unit = {
    val ids = (1 to 1000).map(i => ClientId(Id.validate("item-" + i).toOption.get))
    val entries = ids.zipWithIndex.map { case (id, index) =>
      id -> (if (index == 0) Set.empty[ClientId] else Set(ids(index - 1)))
    }
    val (results, context) = process(entries.reverse).block()
    assertThat(results.size).isEqualTo(1000)
    assertThat(results.values.forall(_.isRight)).isTrue()
    assertThat(context.retrieveServerId(ids.last)).isEqualTo(Some(serverId(ids.last)))
  }

}
