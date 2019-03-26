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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class DurationParser {

    private static final String PATTERN_STRING = "\\s*([0-9]+)\\s*([a-z,A-Z]*)\\s*";
    private static final int AMOUNT = 1;
    private static final int UNIT = 2;

    private static Pattern PATTERN = Pattern.compile(PATTERN_STRING);

    private enum Unit {
        MILLI_SECONDS(ImmutableList.of("ms", "msec", "msecs"), ChronoUnit.MILLIS),
        SECONDS(ImmutableList.of("s", "sec", "secs", "second", "seconds"), ChronoUnit.SECONDS),
        MINUTES(ImmutableList.of("m", "min", "mins", "minute", "minutes"), ChronoUnit.MINUTES),
        HOURS(ImmutableList.of("h", "hour", "hours"), ChronoUnit.HOURS),
        DAYS(ImmutableList.of("d", "day", "days"), ChronoUnit.DAYS);

        public static ChronoUnit parse(String string) {
            return Arrays.stream(values())
                .filter(value -> value.validPatterns.contains(string.toLowerCase(Locale.US)))
                .findFirst()
                .map(entry -> entry.unit)
                .orElseThrow(() -> new NumberFormatException("Unknown unit: " + string));
        }

        private final List<String> validPatterns;
        private final ChronoUnit unit;

        Unit(List<String> validPatterns, ChronoUnit unit) {
            this.validPatterns = validPatterns;
            this.unit = unit;
        }

    }

    // Get sure it can not be instantiated
    private DurationParser() {
    }

    /**
     * Helper method to get the milliseconds for the given rawstring. Allowed
     * rawstrings must match pattern: "\\s*([0-9]+)\\s*([a-z,A-Z]+)\\s*"
     * 
     * @param rawString
     *            The rawstring which we use to extract the amount and unit
     * @return The duration
     * @throws NumberFormatException
     *             Get thrown if an illegal rawString was used
     */
    public static Duration parse(String rawString) throws NumberFormatException {
        return parse(rawString, ChronoUnit.MILLIS);
    }

    public static Duration parse(String rawString, ChronoUnit defaultUnit) throws NumberFormatException {
        Matcher res = PATTERN.matcher(rawString);
        if (res.matches()) {
            if (res.group(AMOUNT) != null && res.group(UNIT) != null) {
                long time = Integer.parseInt(res.group(AMOUNT).trim());
                return parseUnitAsDuration(res.group(UNIT))
                    .orElse(defaultUnit.getDuration())
                    .multipliedBy(time);
            }
        }
        throw new NumberFormatException("The supplied String is not a supported format " + rawString);
    }

    private static Optional<Duration> parseUnitAsDuration(String unit) {
        if (Strings.isNullOrEmpty(unit)) {
            return Optional.empty();
        }
        return Optional.of(Unit.parse(unit).getDuration());
    }
}
