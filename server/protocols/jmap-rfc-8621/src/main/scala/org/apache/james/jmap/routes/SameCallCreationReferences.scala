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

import play.api.libs.json.{JsArray, JsObject, JsPath, JsString}

/**
 * Target references to a creation id that the same invocation creates (`localCreationIds`, read after resolving a
 * `#create` result reference) must not be resolved before the invocation runs:
 * the incoming context maps them to an older object, while RFC 8620 §5.3 processes creates before updates and destroys.
 *
 * Such targets are left as `#creationId` so a method that does not resolve them itself reports them as not updated
 * or not destroyed instead of acting on the older object.
 */
object SameCallCreationReferences {
  private val TARGET_KEY_PROPERTIES: Seq[String] = Seq("update", "onSuccessUpdateEmail")
  private val TARGET_ELEMENT_PROPERTIES: Seq[String] = Seq("destroy", "onSuccessDestroyEmail")

  def targetPaths(arguments: JsObject, localCreationIds: Set[String]): Set[JsPath] =
    if (localCreationIds.isEmpty) {
      Set.empty
    } else {
      def isLocalReference(value: String): Boolean = value.startsWith("#") && localCreationIds.contains(value.substring(1))

      val keyPaths = TARGET_KEY_PROPERTIES.flatMap(property => (arguments \ property).asOpt[JsObject].toSeq
        .flatMap(_.keys.filter(isLocalReference).map(key => JsPath \ property \ key)))
      val elementPaths = TARGET_ELEMENT_PROPERTIES.flatMap(property => (arguments \ property).asOpt[JsArray].toSeq
        .flatMap(_.value.zipWithIndex.collect {
          case (JsString(value), index) if isLocalReference(value) => (JsPath \ property)(index)
        }))

      (keyPaths ++ elementPaths).toSet
    }
}
