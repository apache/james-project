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

package org.apache.james.backends.redis;

import java.util.concurrent.TimeUnit;

import org.apache.james.core.healthcheck.Result;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import reactor.core.publisher.Mono;

public abstract class RedisHealthCheckTest {
  private static final ConditionFactory AWAIT = Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(20, TimeUnit.SECONDS);

  protected abstract RedisHealthCheck getRedisHealthCheck();

  protected abstract void pauseRedis();

  protected abstract void unpauseRedis();

  @Test
  public void checkShouldReturnHealthyWhenRedisIsRunning() {
    Result result = Mono.from(getRedisHealthCheck().check()).block();
    assertThat(result.isHealthy()).isTrue();
  }

  @Test
  public void checkShouldReturnDegradedWhenRedisIsDown() {
    pauseRedis();

    AWAIT.untilAsserted(() -> assertThat(Mono.from(getRedisHealthCheck().check()).block().isDegraded()).isTrue());
  }

  @Test
  public void checkShouldReturnHealthyWhenRedisIsRecovered() {
    pauseRedis();
    unpauseRedis();

    AWAIT.untilAsserted(() -> assertThat(Mono.from(getRedisHealthCheck().check()).block().isHealthy()).isTrue());
  }

  @Test
  public void multipleCheckInShortPeriodShouldReturnHealthyWhenRedisIsRunning() {
    Result check1 = Mono.from(getRedisHealthCheck().check()).block();
    Result check2 = Mono.from(getRedisHealthCheck().check()).block();
    Result check3 = Mono.from(getRedisHealthCheck().check()).block();

    SoftAssertions.assertSoftly(softly -> {
        softly.assertThat(check1.isHealthy()).isTrue();
        softly.assertThat(check2.isHealthy()).isTrue();
        softly.assertThat(check3.isHealthy()).isTrue();
    });
  }

  @Test
  public void  multipleCheckInShortPeriodShouldReturnMixedResultWhenRedisIsUnstable() {
    Result check1 = Mono.from(getRedisHealthCheck().check()).block();

    pauseRedis();
    Result check2 = Mono.from(getRedisHealthCheck().check()).block();

    unpauseRedis();
    Result check3 = Mono.from(getRedisHealthCheck().check()).block();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(check1.isHealthy()).isTrue();
      softly.assertThat(check2.isDegraded()).isTrue();
      softly.assertThat(check3.isHealthy()).isTrue();
    });
  }
}
