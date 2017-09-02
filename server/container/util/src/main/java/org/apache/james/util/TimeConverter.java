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

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeConverter {

    private static final HashMap<String, Integer> multipliers = new HashMap<>(10);

    private static final String PATTERN_STRING = "\\s*([0-9]+)\\s*([a-z,A-Z]*)\\s*";

    private static Pattern PATTERN = null;

    static {
        // add allowed units and their respective multiplier
        multipliers.put("", 1);
        multipliers.put("ms", 1);
        multipliers.put("msec", 1);
        multipliers.put("msecs", 1);
        multipliers.put("s", 1000);
        multipliers.put("sec", 1000);
        multipliers.put("secs", 1000);
        multipliers.put("m", 1000 * 60);
        multipliers.put("minute", 1000 * 60);
        multipliers.put("minutes", 1000 * 60);
        multipliers.put("h", 1000 * 60 * 60);
        multipliers.put("hour", 1000 * 60 * 60);
        multipliers.put("hours", 1000 * 60 * 60);
        multipliers.put("d", 1000 * 60 * 60 * 24);
        multipliers.put("day", 1000 * 60 * 60 * 24);
        multipliers.put("days", 1000 * 60 * 60 * 24);

        PATTERN = Pattern.compile(PATTERN_STRING);

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
        Object multiplierObject = multipliers.get(unit.toLowerCase(Locale.US));
        if (multiplierObject == null) {
            throw new NumberFormatException("Unknown unit: " + unit);
        }
        int multiplier = (Integer) multiplierObject;
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
        PATTERN = Pattern.compile(PATTERN_STRING);
        Matcher res = PATTERN.matcher(rawString);
        if (res.matches()) {

            if (res.group(1) != null && res.group(2) != null) {
                long time = Integer.parseInt(res.group(1).trim());
                String unit = res.group(2);
                return getMilliSeconds(time, unit);
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
