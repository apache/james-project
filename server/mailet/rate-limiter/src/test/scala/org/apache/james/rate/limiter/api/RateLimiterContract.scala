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

import eu.timepit.refined.auto._
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

case class TestKey(value: String) extends RateLimitingKey {
 override def asString(): String = value
}

trait RateLimiterContract {
  private val rules = Rules(Seq(Rule(4L, Duration.ofSeconds(2))))

  def testee(): RateLimiterFactory

  def sleep(duration: Duration): Unit

  @Test
  def subsequentRequestsBelowLimitsWithoutPauseShouldBeAcceptable(): Unit = {
   val rateLimiter = testee().withSpecification(rules)

   SoftAssertions.assertSoftly(softly => {
    (1 to 4).foreach(_ => {
     val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
     assertThat(actual).isEqualTo(AcceptableRate)
    })
   })
  }

  @Test
  def subsequentRequestsAtLimitsWithoutPauseShouldBeAcceptable(): Unit = {
   val rateLimiter = testee().withSpecification(rules)

   val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 4)).block()
   assertThat(actual).isEqualTo(AcceptableRate)
  }

 @Test
 def subsequentRequestsOverLimitsWithoutPauseShouldBeExceeded(): Unit = {
  val rateLimiter = testee().withSpecification(rules)

  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 5)).block()
  assertThat(actual).isEqualTo(RateExceeded)
 }

 @Test
 def subsequentRequestsByBlockOverLimitsWithoutPauseShouldBeExceeded(): Unit = {
  val rateLimiter = testee().withSpecification(rules)

  SMono(rateLimiter.rateLimit(TestKey("key1"), 2)).block()
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 3)).block()
  assertThat(actual).isEqualTo(RateExceeded)
 }

 @Test
 def subsequentRequestsByBlockOverLimitsWithSmallPauseShouldBeExceeded(): Unit = {
  val rateLimiter = testee().withSpecification(Rules(Seq(Rule(4L, Duration.ofSeconds(20)))))

  SMono(rateLimiter.rateLimit(TestKey("key1"), 2)).block()
  sleep(Duration.ofSeconds(1))
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 3)).block()
  assertThat(actual).isEqualTo(RateExceeded)
 }

 @Test
 def subsequentRequestsByBlockOverLimitsWithLongPauseShouldBeAcceptable(): Unit = {
  val rateLimiter = testee().withSpecification(rules)

  SMono(rateLimiter.rateLimit(TestKey("key1"), 2)).block()
  sleep(Duration.ofSeconds(3))
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 3)).block()
  assertThat(actual).isEqualTo(AcceptableRate)
 }

 @Test
 def rateLimitingShouldBePartitionedByKey(): Unit = {
  val rateLimiter = testee().withSpecification(rules)

  SMono(rateLimiter.rateLimit(TestKey("key1"), 2)).block()
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key2"), 3)).block()
  assertThat(actual).isEqualTo(AcceptableRate)
 }

 @Test
 def shouldFailWhenUpperLimitExceeded(): Unit = {
  val rateLimiter = testee().withSpecification(Rules(Seq(
    Rule(1L, Duration.ofSeconds(1)),
    Rule(2L, Duration.ofSeconds(20)))))

  SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  sleep(Duration.ofMillis(1100))
  SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  sleep(Duration.ofMillis(1100))
  // 3 requests in less than 5 s -> fail
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  assertThat(actual).isEqualTo(RateExceeded)
 }

 @Test
 def shouldFailWhenLowerLimitExceeded(): Unit = {
  val rateLimiter = testee().withSpecification(Rules(Seq(
   Rule(2L, Duration.ofSeconds(5)),
   Rule(1L, Duration.ofSeconds(1)))))

  SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  // 2 requests in less than 1 s -> fail
  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  assertThat(actual).isEqualTo(RateExceeded)
 }

 @Test
 def shouldSucceedWhenBothRulesAreRespected(): Unit = {
  val rateLimiter = testee().withSpecification(Rules(Seq(
   Rule(2L, Duration.ofSeconds(5)),
   Rule(1L, Duration.ofSeconds(1)))))

  SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  sleep(Duration.ofSeconds(1))
  SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  sleep(Duration.ofSeconds(4))

  val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 1)).block()
  assertThat(actual).isEqualTo(AcceptableRate)
 }
}
