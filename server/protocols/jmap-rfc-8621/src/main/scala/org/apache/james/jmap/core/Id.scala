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

package org.apache.james.jmap.core

import com.google.common.base.CharMatcher
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}

object Id {

  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))

  case class IdConstraint()

  implicit val validateId: Validate.Plain[String, IdConstraint] =
    Validate.fromPredicate(s => s.length > 0 && s.length < 256 && charMatcher.matchesAllOf(s),
      s => s"'$s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars",
      IdConstraint())

  type Id = String Refined IdConstraint

  def validate(string: String): Either[IllegalArgumentException, Id] =
    refined.refineV[IdConstraint](string)
      .left
      .map(new IllegalArgumentException(_))
}
