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

package org.apache.james.transport.mailets.remote.delivery;

import java.time.Duration;
import java.util.List;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DelaysAndMaxRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelaysAndMaxRetry.class);

    public static DelaysAndMaxRetry defaults() {
        return new DelaysAndMaxRetry(RemoteDeliveryConfiguration.DEFAULT_MAX_RETRY, Repeat.repeat(new Delay(), RemoteDeliveryConfiguration.DEFAULT_MAX_RETRY));
    }

    public static DelaysAndMaxRetry from(int intendedMaxRetries, String delaysAsString) throws MessagingException {
        List<Delay> delayTimesList = createDelayList(delaysAsString);
        int totalAttempts = computeTotalAttempts(delayTimesList);
        return getDelaysAndMaxRetry(intendedMaxRetries, totalAttempts, delayTimesList);
    }

    private static DelaysAndMaxRetry getDelaysAndMaxRetry(int intendedMaxRetries, int totalAttempts, List<Delay> delayTimesList) throws MessagingException {
        if (totalAttempts > intendedMaxRetries) {
            LOGGER.warn("Total number of delayTime attempts exceeds maxRetries specified. Increasing maxRetries from {} to {}", intendedMaxRetries, totalAttempts);
            return new DelaysAndMaxRetry(totalAttempts, delayTimesList);
        } else {
            int extra = intendedMaxRetries - totalAttempts;
            if (extra > 0) {
                LOGGER.warn("maxRetries is larger than total number of attempts specified. Increasing last delayTime with {} attempts ", extra);
                return addExtraAttemptToLastDelay(intendedMaxRetries, extra, delayTimesList);
            }
            return new DelaysAndMaxRetry(intendedMaxRetries, delayTimesList);
        }
    }

    private static DelaysAndMaxRetry addExtraAttemptToLastDelay(int intendedMaxRetries, int extra, List<Delay> delayTimesList) throws MessagingException {
        if (!delayTimesList.isEmpty()) {
            Delay lastDelay = delayTimesList.get(delayTimesList.size() - 1);
            Duration lastDelayTime = lastDelay.getDelayTime();
            LOGGER.warn("Delay of {} is now attempted: {} times",
                DurationFormatUtils.formatDurationWords(lastDelayTime.toMillis(), true, true),
                lastDelay.getAttempts());
            return new DelaysAndMaxRetry(intendedMaxRetries,
                ImmutableList.copyOf(
                    Iterables.concat(
                        Iterables.limit(delayTimesList, delayTimesList.size() - 1),
                        ImmutableList.of(new Delay(lastDelay.getAttempts() + extra, lastDelayTime)))));
        } else {
            throw new MessagingException("No delaytimes, cannot continue");
        }
    }

    private static List<Delay> createDelayList(String delaysAsString) {
        if (delaysAsString == null) {
            // Use default delayTime.
            return ImmutableList.of(new Delay());
        }

        List<String> delayStrings = Splitter.on(',')
            .omitEmptyStrings()
            .trimResults()
            .splitToList(delaysAsString);

        ImmutableList.Builder<Delay> builder = ImmutableList.builder();
        try {
            for (String s : delayStrings) {
                builder.add(Delay.from(s));
            }
            return builder.build();
        } catch (Exception e) {
            LOGGER.warn("Invalid delayTime setting: {}", delaysAsString);
            return builder.build();
        }
    }

    private static int computeTotalAttempts(List<Delay> delayList) {
        return delayList.stream()
            .mapToInt(Delay::getAttempts)
            .sum();
    }

    private final int maxRetries;
    private final List<Delay> delays;

    @VisibleForTesting
    DelaysAndMaxRetry(int maxRetries, List<Delay> delays) {
        this.maxRetries = maxRetries;
        this.delays = ImmutableList.copyOf(delays);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * <p>
     * This method expands an ArrayList containing Delay objects into an array
     * holding the only delaytime in the order.
     * </p>
     * <p/>
     * So if the list has 2 Delay objects the first having attempts=2 and
     * delaytime 4000 the second having attempts=1 and delaytime=300000 will be
     * expanded into this array:
     * <p/>
     * <pre>
     * long[0] = 4000
     * long[1] = 4000
     * long[2] = 300000
     * </pre>
     *
     * @param list the list to expand
     * @return the expanded list
     */
    public ImmutableList<Duration> getExpandedDelays() {
        ImmutableList.Builder<Duration> builder = ImmutableList.builder();
        for (Delay delay: delays) {
            builder.addAll(delay.getExpendendDelays());
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DelaysAndMaxRetry) {
            DelaysAndMaxRetry that = (DelaysAndMaxRetry) o;
            return Objects.equal(this.maxRetries, that.maxRetries)
                && Objects.equal(this.delays, that.delays);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(maxRetries, delays);
    }
}
