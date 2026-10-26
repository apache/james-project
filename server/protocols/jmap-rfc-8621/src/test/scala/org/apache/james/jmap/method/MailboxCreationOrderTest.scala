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

package org.apache.james.jmap.method

import org.apache.james.jmap.core.Id
import org.apache.james.jmap.mail.MailboxCreationId
import org.apache.james.jmap.method.MailboxSetCreatePerformer.{CreationOrder, orderCreations}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap

class MailboxCreationOrderTest extends AnyWordSpec with Matchers {
  private def id(value: String): MailboxCreationId = MailboxCreationId(Id.validate(value).toOption.get)

  "Mailbox creation ordering" should {
    "accept an empty create" in {
      orderCreations(Map.empty) should equal(CreationOrder(Seq.empty, Set.empty))
    }

    "create parents before children for every permutation of a forest" in {
      val dependencies = Seq(
        id("parent") -> None,
        id("child") -> Some(id("parent")),
        id("grandchild") -> Some(id("child")),
        id("sibling") -> Some(id("parent")),
        id("independent") -> None)
      dependencies.permutations.foreach { permutation =>
        val order = orderCreations(ListMap.from(permutation))
        order.unresolved shouldBe empty
        order.ordered.toSet should equal(dependencies.map(_._1).toSet)
        order.ordered.size should equal(dependencies.size)
        dependencies.foreach { case (child, parent) =>
          parent.foreach(parent => order.ordered.indexOf(parent) should be < order.ordered.indexOf(child))
        }
      }
    }

    "isolate self references, cycles and descendants from independent trees" in {
      val order = orderCreations(Map(
        id("self") -> Some(id("self")),
        id("a") -> Some(id("b")),
        id("b") -> Some(id("a")),
        id("descendant") -> Some(id("a")),
        id("parent") -> None,
        id("child") -> Some(id("parent"))))
      order.unresolved should equal(Set(id("self"), id("a"), id("b"), id("descendant")))
      order.ordered should equal(Seq(id("parent"), id("child")))
    }

    "leave references outside the create map for context resolution" in {
      orderCreations(Map(id("child") -> Some(id("previous")))) should equal(
        CreationOrder(Seq(id("child")), Set.empty))
    }

    "handle deep trees without recursive traversal" in {
      val ids = (0 until 10000).map(i => id(s"mailbox$i"))
      val dependencies = ids.zipWithIndex.map { case (id, index) =>
        id -> (if (index == 0) None else Some(ids(index - 1)))
      }
      orderCreations(ListMap.from(dependencies.reverse)) should equal(CreationOrder(ids, Set.empty))
    }
  }
}
