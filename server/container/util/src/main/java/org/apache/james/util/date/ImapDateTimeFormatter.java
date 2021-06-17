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
package org.apache.james.util.date;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

import com.google.common.collect.ImmutableMap;

public class ImapDateTimeFormatter {

    private static final int INITIAL_YEAR = 1970;
    public static final DateTimeFormatter RFC_5322_FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .parseLenient()
        .optionalStart()
            .appendText(DAY_OF_WEEK, dayOfWeek())
            .appendLiteral(", ")
        .optionalEnd()
        .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral(' ')
        .appendText(MONTH_OF_YEAR, monthOfYear())
        .appendLiteral(' ')
        .appendValueReduced(YEAR, 2, 4, INITIAL_YEAR)
        .appendLiteral(' ')
        .appendValue(HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(MINUTE_OF_HOUR, 2)
        .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
        .optionalEnd()
        .appendLiteral(' ')
        .appendOffset("+HHMM", "GMT")
        .toFormatter();

    public static DateTimeFormatter rfc5322() {
        return RFC_5322_FORMATTER;
    }

    private static ImmutableMap<Long, String> monthOfYear() {
        return ImmutableMap.<Long, String>builder()
                .put(1L, "Jan")
                .put(2L, "Feb")
                .put(3L, "Mar")
                .put(4L, "Apr")
                .put(5L, "May")
                .put(6L, "Jun")
                .put(7L, "Jul")
                .put(8L, "Aug")
                .put(9L, "Sep")
                .put(10L, "Oct")
                .put(11L, "Nov")
                .put(12L, "Dec")
                .build();
    }

    private static ImmutableMap<Long, String> dayOfWeek() {
        return ImmutableMap.<Long, String>builder()
                .put(1L, "Mon")
                .put(2L, "Tue")
                .put(3L, "Wed")
                .put(4L, "Thu")
                .put(5L, "Fri")
                .put(6L, "Sat")
                .put(7L, "Sun")
                .build();
    }

}
