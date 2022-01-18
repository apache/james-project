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

package org.apache.james.rate.limiter.memory

import es.moki.ratelimitj.core.limiter.request.{RequestLimitRule, RequestRateLimiter}
import es.moki.ratelimitj.inmemory.request.InMemorySlidingWindowRequestRateLimiter
import org.apache.james.rate.limiter.api.Quantity.Quantity
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiter, RateLimiterFactory, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class MemoryRateLimiterFactory() extends RateLimiterFactory {
  override def withSpecification(rules: Rules): RateLimiter = {
    MemoryRateLimiter(new InMemorySlidingWindowRequestRateLimiter(rules.rules.map(convert).toSet.asJava))
  }

  private def convert(rule: Rule): RequestLimitRule = RequestLimitRule.of(rule.duration, rule.quantity.value)
}

case class MemoryRateLimiter(limiter: RequestRateLimiter) extends RateLimiter {
  override def rateLimit(key: RateLimitingKey, increaseQuantity: Quantity): Publisher[RateLimitingResult] = 
    if (limiter.overLimitWhenIncremented(key.asString(), increaseQuantity.value)) {
      SMono.just(RateExceeded)
    } else {
      SMono.just(AcceptableRate)
    }
}
