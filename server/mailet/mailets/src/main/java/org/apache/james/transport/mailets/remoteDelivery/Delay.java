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

package org.apache.james.transport.mailets.remoteDelivery;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;

import org.apache.james.transport.util.Patterns;
import org.apache.james.util.TimeConverter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

/**
 * This class is used to hold a delay time and its corresponding number of
 * retries.
 */
public class Delay {

    /**
     * Default Delay Time (Default is 6*60*60*1000 Milliseconds (6 hours)).
     */
    public static final long DEFAULT_DELAY_TIME = 21600000;
    public static final int DEFAULT_ATTEMPTS = 1;
    /**
     * Pattern to match [attempts*]delay[units].
     */
    private static final String PATTERN_STRING = "\\s*([0-9]*\\s*[\\*])?\\s*([0-9]+)\\s*([a-z,A-Z]*)\\s*";
    private static final Pattern PATTERN = Patterns.compilePatternUncheckedException(PATTERN_STRING);

    private int attempts = DEFAULT_ATTEMPTS;

    private long delayTime = DEFAULT_DELAY_TIME;

    /**
     * <p>
     * This constructor expects Strings of the form
     * "[attempt\*]delaytime[unit]".
     * </p>
     * <p>
     * The optional attempt is the number of tries this delay should be used
     * (default = 1). The unit, if present, must be one of
     * (msec,sec,minute,hour,day). The default value of unit is 'msec'.
     * </p>
     * <p>
     * The constructor multiplies the delaytime by the relevant multiplier
     * for the unit, so the delayTime instance variable is always in msec.
     * </p>
     *
     * @param initString the string to initialize this Delay object from
     */
    public Delay(String initString) throws MessagingException {
        // Default unit value to 'msec'.
        String unit = "msec";

        Matcher res = PATTERN.matcher(initString);
        if (res.matches()) {
            // The capturing groups will now hold:
            // at 1: attempts * (if present)
            // at 2: delaytime
            // at 3: unit (if present)
            if (res.group(1) != null && !res.group(1).equals("")) {
                // We have an attempt *
                String attemptMatch = res.group(1);

                // Strip the * and whitespace.
                attemptMatch = attemptMatch.substring(0, attemptMatch.length() - 1).trim();
                attempts = Integer.parseInt(attemptMatch);
            }

            delayTime = Long.parseLong(res.group(2));

            if (!res.group(3).equals("")) {
                // We have a value for 'unit'.
                unit = res.group(3).toLowerCase(Locale.US);
            }
        } else {
            throw new MessagingException(initString + " does not match " + PATTERN_STRING);
        }

        // calculate delayTime.
        try {
            delayTime = TimeConverter.getMilliSeconds(delayTime, unit);
        } catch (NumberFormatException e) {
            throw new MessagingException(e.getMessage());
        }
    }

    /**
     * This constructor makes a default Delay object with attempts = 1 and
     * delayTime = DEFAULT_DELAY_TIME.
     */
    public Delay() {
    }

    @VisibleForTesting
    Delay(int attempts, long delayTime) {
        this.attempts = attempts;
        this.delayTime = delayTime;
    }

    /**
     * @return the delayTime for this Delay
     */
    public long getDelayTime() {
        return delayTime;
    }

    /**
     * @return the number attempts this Delay should be used.
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * Set the number attempts this Delay should be used.
     */
    public void setAttempts(int value) {
        attempts = value;
    }

    /**
     * Pretty prints this Delay
     */
    @Override
    public String toString() {
        return getAttempts() + "*" + getDelayTime() + "msecs";
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
