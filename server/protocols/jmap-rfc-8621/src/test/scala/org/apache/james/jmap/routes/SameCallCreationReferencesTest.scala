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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsObject, JsPath, Json}

class SameCallCreationReferencesTest {
  private def targetPaths(json: String): Set[JsPath] = {
    val arguments = Json.parse(json).as[JsObject]
    SameCallCreationReferences.targetPaths(arguments, ProcessingContext(Map.empty, Map.empty).declaredCreationIds(arguments))
  }

  @Test
  def shouldReturnNothingWithoutCreate(): Unit =
    assertThat(targetPaths("""{"update": {"#x": {}}, "destroy": ["#x"]}""")).isEqualTo(Set.empty)

  @Test
  def shouldReturnUpdateKeysAndDestroyElementsCreatedByTheSameCall(): Unit =
    assertThat(targetPaths(
      """{
        |  "create": {"x": {}},
        |  "update": {"#x": {}, "#y": {}, "x": {}},
        |  "destroy": ["#y", "#x", "x"]
        |}""".stripMargin))
      .isEqualTo(Set(JsPath \ "update" \ "#x", (JsPath \ "destroy")(1)))

  @Test
  def shouldReturnEmailSubmissionOnSuccessTargetsCreatedByTheSameCall(): Unit =
    assertThat(targetPaths(
      """{
        |  "create": {"k1": {}},
        |  "onSuccessUpdateEmail": {"#k1": {"keywords/$draft": null}, "#k2": {}},
        |  "onSuccessDestroyEmail": ["#k2", "#k1"]
        |}""".stripMargin))
      .isEqualTo(Set(JsPath \ "onSuccessUpdateEmail" \ "#k1", (JsPath \ "onSuccessDestroyEmail")(1)))

  @Test
  def shouldIgnoreReferencesOutsideTargets(): Unit =
    assertThat(targetPaths(
      """{
        |  "create": {"x": {"parentId": "#x"}},
        |  "update": {"other": {"parentId": "#x"}}
        |}""".stripMargin))
      .isEqualTo(Set.empty)
}
