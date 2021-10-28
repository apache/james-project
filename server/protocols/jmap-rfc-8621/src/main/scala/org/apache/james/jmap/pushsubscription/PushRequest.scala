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

package org.apache.james.jmap.pushsubscription

import com.google.common.base.CharMatcher
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import org.apache.james.jmap.pushsubscription.PushTTL.PushTTL
import org.apache.james.jmap.pushsubscription.PushTopic.PushTopic

object PushUrgency {
  def default: PushUrgency = Normal
}

sealed trait PushUrgency {
  def value: String
}

case object Low extends PushUrgency {
  val value: String = "low"
}

case object VeryLow extends PushUrgency {
  val value: String = "very-low"
}

case object Normal extends PushUrgency {
  val value: String = "normal"
}

case object High extends PushUrgency {
  val value: String = "high"
}

object PushTopic {
  type PushTopic = String Refined PushTopicConstraint
  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('='))

  implicit val validateTopic: Validate.Plain[String, PushTopicConstraint] =
    Validate.fromPredicate(s => s.nonEmpty && s.length <= 32 && charMatcher.matchesAllOf(s),
      s => s"'$s' contains some invalid characters. Should use base64 alphabet and be no longer than 32 chars",
      PushTopicConstraint())

  def validate(string: String): Either[IllegalArgumentException, PushTopic] =
    refined.refineV[PushTopicConstraint](string)
      .left
      .map(new IllegalArgumentException(_))
}

object PushTTL {
  type PushTTL = Long Refined PushTTLConstraint

  private val MAX_INT = 2147483647L

  implicit val validateTTL: Validate.Plain[Long, PushTTLConstraint] =
    Validate.fromPredicate(s => s >= 0 && s <= MAX_INT,
      s => s"'$s' invalid. Should be non-negative numeric and no greater than 2^31",
      PushTTLConstraint())


  def validate(value: Long): Either[IllegalArgumentException, PushTTL] =
    refined.refineV[PushTTLConstraint](value)
      .left
      .map(new IllegalArgumentException(_))

  val MAX: PushTTL = validate(MAX_INT).toOption.get
}

case class PushTopicConstraint()

case class PushTTLConstraint()

case class PushRequest(ttl: PushTTL,
                       topic: Option[PushTopic],
                       urgency: Option[PushUrgency],
                       payload: Array[Byte])
