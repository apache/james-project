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
package org.apache.james.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class TimeConverter {

    private static final String PATTERN_STRING = "\\s*([0-9]+)\\s*([a-z,A-Z]*)\\s*";

    private static Pattern PATTERN = Pattern.compile(PATTERN_STRING);

    public enum Unit {
        MILLI_SECONDS(ImmutableList.of("ms", "msec", "msecs"), 1),
        SECONDS(ImmutableList.of("s", "sec", "secs"), 1000),
        MINUTES(ImmutableList.of("m", "min", "mins", "minute", "minutes"), 1000 * 60),
        HOURS(ImmutableList.of("h", "hour", "hours"), 1000 * 60 * 60),
        DAYS(ImmutableList.of("d", "day", "days"), 1000 * 60 * 60 * 24);

        public static Unit parse(String string) {
            return Arrays.stream(values())
                .filter(value -> value.validPatterns.contains(string.toLowerCase(Locale.US)))
                .findFirst()
                .orElseThrow(() -> new NumberFormatException("Unknown unit: " + string));
        }

        private final List<String> validPatterns;
        private final int multiplier;

        Unit(List<String> validPatterns, int multiplier) {
            this.validPatterns = validPatterns;
            this.multiplier = multiplier;
        }

    }

    // Get sure it can not be instantiated
    private TimeConverter() {
    }

    /**
     * Helper method to get the milliseconds for the given amount and unit
     * 
     * @param amount
     *            The amount for use with the unit
     * @param unit
     *            The unit
     * @return The time in milliseconds
     * @throws NumberFormatException
     *             Get thrown if an illegal unit was used
     */
    public static long getMilliSeconds(long amount, String unit) throws NumberFormatException {
        return getMilliSeconds(amount, Unit.parse(unit));
    }

    public static long getMilliSeconds(long amount, Unit unit) throws NumberFormatException {
        int multiplier = unit.multiplier;
        return (amount * multiplier);
    }

    /**
     * Helper method to get the milliseconds for the given rawstring. Allowed
     * rawstrings must match pattern: "\\s*([0-9]+)\\s*([a-z,A-Z]+)\\s*"
     * 
     * @param rawString
     *            The rawstring which we use to extract the amount and unit
     * @return The time in milliseconds
     * @throws NumberFormatException
     *             Get thrown if an illegal rawString was used
     */
    public static long getMilliSeconds(String rawString) throws NumberFormatException {
        return getMilliSeconds(rawString, Unit.MILLI_SECONDS);
    }

    public static long getMilliSeconds(String rawString, Unit defaultUnit) throws NumberFormatException {
        Matcher res = PATTERN.matcher(rawString);
        if (res.matches()) {

            if (res.group(1) != null && res.group(2) != null) {
                long time = Integer.parseInt(res.group(1).trim());
                String unit = res.group(2);
                if (Strings.isNullOrEmpty(unit)) {
                    return getMilliSeconds(time, defaultUnit);
                }
                return getMilliSeconds(time, Unit.parse(unit));
            } else {
                // This should never Happen anyway throw an exception
                throw new NumberFormatException("The supplied String is not a supported format " + rawString);
            }
        } else {
            // The rawString not match our pattern. So its not supported
            throw new NumberFormatException("The supplied String is not a supported format " + rawString);
        }
    }

}
