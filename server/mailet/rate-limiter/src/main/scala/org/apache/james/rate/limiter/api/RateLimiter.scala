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
import org.apache.james.rate.limiter.api.AllowedQuantity.AllowedQuantity
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.mailet.MailetConfig
import org.reactivestreams.Publisher

trait RateLimitingKey {
    def asString(): String
}

object AllowedQuantity {
  type PositiveLongConstraint = Positive
  type AllowedQuantity = Long Refined PositiveLongConstraint

  def validate(value: Long): Either[NumberFormatException, AllowedQuantity] =
    refined.refineV[PositiveLongConstraint](value) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new NumberFormatException(error))
    }

  def liftOrThrow(value: Long): AllowedQuantity =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }
}

object Increment {
  type PositiveLongConstraint = Positive
  type Increment = Int Refined PositiveLongConstraint

  def validate(value: Int): Either[NumberFormatException, Increment] =
    refined.refineV[PositiveLongConstraint](value) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new NumberFormatException(error))
    }

  def liftOrThrow(value: Int): Increment =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }
}

case class Rule(quantity: AllowedQuantity, duration: Duration)
case class Rules(rules: Seq[Rule])

trait RateLimiter {
    def rateLimit(key: RateLimitingKey, increaseQuantity: Increment): Publisher[RateLimitingResult]
}

trait RateLimiterFactory {
   def withSpecification(rules: Rules): RateLimiter
}

trait RateLimiterFactoryProvider {
  def create(mailetConfig: MailetConfig): RateLimiterFactory
}

sealed trait RateLimitingResult {
  def merge(other: RateLimitingResult): RateLimitingResult
}
case object RateExceeded extends RateLimitingResult {
  override def merge(other: RateLimitingResult): RateLimitingResult = RateExceeded
}
case object AcceptableRate extends RateLimitingResult {
  override def merge(other: RateLimitingResult): RateLimitingResult = other
}