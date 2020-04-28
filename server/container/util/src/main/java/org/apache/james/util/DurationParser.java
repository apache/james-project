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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class DurationParser {
    private enum Unit {
        MILLI_SECONDS(ImmutableList.of("ms", "msec", "msecs"), ChronoUnit.MILLIS),
        SECONDS(ImmutableList.of("s", "sec", "secs", "second", "seconds"), ChronoUnit.SECONDS),
        MINUTES(ImmutableList.of("m", "min", "mins", "minute", "minutes"), ChronoUnit.MINUTES),
        HOURS(ImmutableList.of("h", "hour", "hours"), ChronoUnit.HOURS),
        DAYS(ImmutableList.of("d", "day", "days"), ChronoUnit.DAYS),
        WEEKS(ImmutableList.of("w", "week", "weeks"), ChronoUnit.WEEKS),
        MONTH(ImmutableList.of("month", "months"), ChronoUnit.MONTHS),
        YEARS(ImmutableList.of("y", "year", "years"), ChronoUnit.YEARS);

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
        UnitParser.ParsingResult parsingResult = UnitParser.parse(rawString);
        Duration unitAsDuration = parsingResult.getUnit()
            .map(s -> Unit.parse(s).getDuration())
            .orElse(defaultUnit.getDuration());
        return computeDuration(unitAsDuration, parsingResult.getNumber());
    }

    private static Duration computeDuration(Duration unitAsDuration, long time) {
        Preconditions.checkArgument(time >= 0, "Duration amount should be positive");

        return unitAsDuration.multipliedBy(time);
    }
}
