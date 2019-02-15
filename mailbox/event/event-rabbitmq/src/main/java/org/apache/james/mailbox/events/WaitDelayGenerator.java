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

package org.apache.james.mailbox.events;

import java.security.SecureRandom;
import java.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class WaitDelayGenerator {

    static WaitDelayGenerator of(RetryBackoffConfiguration retryBackoff) {
        return new WaitDelayGenerator(retryBackoff);
    }

    private static Duration randomBetween(Duration base, Duration jitter) {
        Preconditions.checkArgument(!jitter.isNegative(), "jitter value should always be positive");
        if (jitter.isZero()) {
            return base;
        }
        long maxJitterAsMillis = jitter.toMillis();
        long jitterAsMillis = SECURE_RANDOM.nextInt(Ints.checkedCast(maxJitterAsMillis * 2)) / 2;
        return base.plusMillis(jitterAsMillis);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RetryBackoffConfiguration retryBackoff;

    private WaitDelayGenerator(RetryBackoffConfiguration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    Mono<Integer> delayIfHaveTo(int retryCount) {
        Mono<Integer> countRetryMono = Mono.just(retryCount);
        if (!shouldDelay(retryCount)) {
            return countRetryMono;
        }

        return countRetryMono
            .delayElement(generateDelay(retryCount), Schedulers.elastic());
    }

    @VisibleForTesting
    Duration generateDelay(int retryCount) {
        if (!shouldDelay(retryCount)) {
            return Duration.ZERO;
        }
        long exponentialFactor = Double.valueOf(Math.pow(2, retryCount - 1)).longValue();
        Duration minDelay = retryBackoff.getFirstBackoff().multipliedBy(exponentialFactor);
        Duration jitterDelay = retryBackoff.getFirstBackoff()
            .multipliedBy(Double.valueOf(retryBackoff.getJitterFactor() * 100).intValue())
            .dividedBy(100);

        return randomBetween(minDelay, jitterDelay);
    }

    private boolean shouldDelay(int retryCount) {
        return retryCount >= 1 && retryCount <= retryBackoff.getMaxRetries();
    }
}
