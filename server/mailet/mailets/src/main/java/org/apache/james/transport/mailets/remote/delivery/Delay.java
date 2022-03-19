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

import org.apache.james.util.DurationParser;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public class Delay {
    /**
     * <p> The optional attempt is the number of tries this delay should be used (default = 1).
     * The delayTime is parsed by {@link DurationParser}</p>
     *
     * @param initString the string to initialize this Delay object from. It has the form "[attempt\*]delaytime[unit]"
     */
    public static Delay from(String initString) throws MessagingException {
        if (Strings.isNullOrEmpty(initString)) {
            throw new NumberFormatException("Null or Empty strings are not permitted");
        }
        List<String> parts = Splitter.on('*').trimResults().splitToList(initString);

        if (parts.size() == 1) {
            return new Delay(DEFAULT_ATTEMPTS, DurationParser.parse(parts.get(0)));
        }
        if (parts.size() == 2) {
            int attempts = Integer.parseInt(parts.get(0));
            if (attempts < 0) {
                throw new MessagingException("Number of attempts negative in " + initString);
            }
            return new Delay(attempts, DurationParser.parse(parts.get(1)));
        }
        throw new MessagingException(initString + " contains too much parts");
    }

    public static final Duration DEFAULT_DELAY_TIME = Duration.ofHours(6);
    public static final int DEFAULT_ATTEMPTS = 1;

    private final int attempts;
    private final Duration delayTime;

    public Delay() {
        this(DEFAULT_ATTEMPTS, DEFAULT_DELAY_TIME);
    }

    Delay(int attempts, Duration delayTime) {
        this.attempts = attempts;
        this.delayTime = delayTime;
    }

    public Duration getDelayTime() {
        return delayTime;
    }

    public int getAttempts() {
        return attempts;
    }

    public List<Duration> getExpendendDelays() {
        return Repeat.repeat(delayTime, attempts);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("attempts", attempts)
            .add("delayTime", delayTime)
            .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Delay) {
            Delay that = (Delay) o;

            return Objects.equal(this.attempts, that.attempts)
                && Objects.equal(this.delayTime, that.delayTime);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attempts, delayTime);
    }
}
