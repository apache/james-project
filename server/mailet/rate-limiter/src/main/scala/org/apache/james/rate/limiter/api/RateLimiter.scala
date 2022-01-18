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

package org.apache.james.rate.limiter.api

import java.time.Duration

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import org.apache.james.rate.limiter.api.Quantity.Quantity
import org.reactivestreams.Publisher

trait RateLimitingKey {
    def asString(): String
}

object Quantity {
  type PositiveLongConstraint = Positive
  type Quantity = Int Refined PositiveLongConstraint

  def validate(value: Int): Either[NumberFormatException, Quantity] =
    refined.refineV[PositiveLongConstraint](value) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new NumberFormatException(error))
    }

  def liftOrThrow(value: Int): Quantity =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }
}

case class Rule(quantity: Quantity, duration: Duration)
case class Rules(rules: Seq[Rule])

trait RateLimiter {
    def rateLimit(key: RateLimitingKey, increaseQuantity: Quantity): Publisher[RateLimitingResult]
}

trait RateLimiterFactory {
   def withSpecification(rules: Rules): RateLimiter
}

sealed trait RateLimitingResult
case object RateExceeded extends RateLimitingResult
case object AcceptableRate extends RateLimitingResult