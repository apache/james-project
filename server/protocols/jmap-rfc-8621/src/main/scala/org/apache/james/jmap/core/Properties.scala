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

package org.apache.james.jmap.core

import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import play.api.libs.json.JsObject

object Properties {
  def empty(): Properties = Properties()

  def apply(values: NonEmptyString*): Properties = Properties(values.toSet)

  def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None, Some(_))
    }))
}

case class Properties(value: Set[NonEmptyString]) {
  def union(other: Properties): Properties = Properties(value.union(other.value))

  def removedAll(other: Properties): Properties = Properties(value.removedAll(other.value))

  def ++(other: Properties): Properties = union(other)

  def --(other: Properties): Properties = removedAll(other)

  def intersect(properties: Properties): Properties = Properties(value.intersect(properties.value))

  def isEmpty(): Boolean = value.isEmpty

  def contains(property: NonEmptyString): Boolean = value.contains(property)
  def containsString(property: String): Boolean = refineV[NonEmpty](property).fold(e => false, refined => contains(refined))

  def format(): String = value.mkString(", ")

  def filter(o: JsObject): JsObject =
    JsObject(o.fields.filter(entry => {
      val refined: Either[String, NonEmptyString] = refineV[NonEmpty](entry._1)
      refined.fold(e => throw new RuntimeException(e),
        property => value.contains(property))
    }))
}
