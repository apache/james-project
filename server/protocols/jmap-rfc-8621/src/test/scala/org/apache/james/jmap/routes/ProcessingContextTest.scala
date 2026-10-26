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

package org.apache.james.jmap.routes

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.{ClientId, ServerId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProcessingContextTest extends AnyWordSpec with Matchers {
  "Creation ID lookup" should {
    "return no mapping for an unknown creation ID" in {
      ProcessingContext(Map.empty, Map.empty).retrieveServerId(ClientId("missing")) should equal(None)
    }

    "retain initial and newly recorded mappings without changing the previous context" in {
      val initial = ProcessingContext(Map(ClientId("initial") -> ServerId("server0")), Map.empty)
      val updated = initial.recordCreatedId(ClientId("parent"), ServerId("server1"))
        .recordCreatedId(ClientId("child"), ServerId("server2"))

      updated.retrieveServerId(ClientId("initial")) should equal(Some(ServerId("server0")))
      updated.retrieveServerId(ClientId("parent")) should equal(Some(ServerId("server1")))
      updated.retrieveServerId(ClientId("child")) should equal(Some(ServerId("server2")))
      initial.retrieveServerId(ClientId("parent")) should equal(None)
    }
  }
}
