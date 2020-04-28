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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

class UnitParser {
    static class ParsingResult {
        private final long number;
        private final Optional<String> unit;

        ParsingResult(long number, Optional<String> unit) {
            this.number = number;
            this.unit = unit;
        }

        public long getNumber() {
            return number;
        }

        public Optional<String> getUnit() {
            return unit;
        }
    }

    private static final String PATTERN_STRING = "\\s*(-?[0-9]+)\\s*([a-z,A-Z]*)\\s*";
    private static final int AMOUNT = 1;
    private static final int UNIT = 2;

    private static Pattern PATTERN = Pattern.compile(PATTERN_STRING);

    static ParsingResult parse(String rawString) throws NumberFormatException {
        Matcher res = PATTERN.matcher(rawString);
        if (res.matches()) {
            String unitAsString = res.group(UNIT);
            String amountAsString = res.group(AMOUNT);
            long amount = Integer.parseInt(amountAsString.trim());

            return new ParsingResult(amount, Optional.of(unitAsString).filter(s -> !Strings.isNullOrEmpty(s)));
        }
        throw new NumberFormatException("Supplied value do not follow the unit format (number optionally suffixed with a string representing the unit");
    }
}
