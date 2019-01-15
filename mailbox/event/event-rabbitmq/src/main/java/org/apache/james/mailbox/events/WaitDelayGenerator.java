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

import reactor.core.publisher.Mono;

class WaitDelayGenerator {

    static WaitDelayGenerator of(RetryBackoffConfiguration retryBackoff) {
        return new WaitDelayGenerator(retryBackoff);
    }

    private static int randomBetween(int lowest, int highest) {
        Preconditions.checkArgument(lowest <= highest, "lowest always has to be less than or equals highest");
        if (lowest == highest) {
            return lowest;
        }
        return SECURE_RANDOM.nextInt(highest - lowest) + lowest;
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
            .delayElement(generateDelay(retryCount));
    }

    @VisibleForTesting
    Duration generateDelay(int retryCount) {
        if (!shouldDelay(retryCount)) {
            return Duration.ZERO;
        }
        int exponentialFactor = Double.valueOf(Math.pow(2, retryCount - 1)).intValue();
        int minDelay = exponentialFactor * (int) retryBackoff.getFirstBackoff().toMillis();
        int maxDelay = Double.valueOf(minDelay + minDelay * retryBackoff.getJitterFactor()).intValue();

        return Duration.ofMillis(randomBetween(minDelay, maxDelay));
    }

    private boolean shouldDelay(int retryCount) {
        return retryCount >= 1 && retryCount <= retryBackoff.getMaxRetries();
    }
}
