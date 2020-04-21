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

package org.apache.james.imap.decode;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;


public class DecoderUtilsLocaleDateTest {

    private static final Locale BASE_DEFAULT_LOCALE = Locale.getDefault();

    @After
    public void tearDown() throws Exception {
        Locale.setDefault(BASE_DEFAULT_LOCALE);
    }

    static Stream<Arguments> decodeDateTimeWithVariousDefaultLocale() {
        //(explicit type arguments speedup compilation and analysis time)
        return
            Stream.<Tuple2<String, String>>of(
                Tuples.of("21-Oct-1972 20:00:00 +0000", "21 Oct 1972 20:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0100", "21 Oct 1972 19:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0200", "21 Oct 1972 18:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0300", "21 Oct 1972 17:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0400", "21 Oct 1972 16:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0500", "21 Oct 1972 15:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0600", "21 Oct 1972 14:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0700", "21 Oct 1972 13:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0800", "21 Oct 1972 12:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0900", "21 Oct 1972 11:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1000", "21 Oct 1972 10:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1100", "21 Oct 1972 09:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1200", "21 Oct 1972 08:00:00 GMT"),

                Tuples.of("21-Oct-1972 20:00:00 +1000", "21 Oct 1972 10:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0100", "21 Oct 1972 21:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0200", "21 Oct 1972 22:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0300", "21 Oct 1972 23:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0400", "22 Oct 1972 00:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0500", "22 Oct 1972 01:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0600", "22 Oct 1972 02:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0700", "22 Oct 1972 03:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0800", "22 Oct 1972 04:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0900", "22 Oct 1972 05:00:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -1000", "22 Oct 1972 06:00:00 GMT"),

                Tuples.of("21-Oct-1972 20:00:00 +0030", "21 Oct 1972 19:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0130", "21 Oct 1972 18:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0230", "21 Oct 1972 17:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0330", "21 Oct 1972 16:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0430", "21 Oct 1972 15:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0530", "21 Oct 1972 14:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0630", "21 Oct 1972 13:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0730", "21 Oct 1972 12:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0830", "21 Oct 1972 11:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +0930", "21 Oct 1972 10:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1030", "21 Oct 1972 09:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1130", "21 Oct 1972 08:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 +1230", "21 Oct 1972 07:30:00 GMT"),

                Tuples.of("21-Oct-1972 20:00:00 -0030", "21 Oct 1972 20:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0130", "21 Oct 1972 21:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0230", "21 Oct 1972 22:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0330", "21 Oct 1972 23:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0430", "22 Oct 1972 00:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0530", "22 Oct 1972 01:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0630", "22 Oct 1972 02:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0730", "22 Oct 1972 03:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0830", "22 Oct 1972 04:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -0930", "22 Oct 1972 05:30:00 GMT"),
                Tuples.of("21-Oct-1972 20:00:00 -1030", "22 Oct 1972 06:30:00 GMT"),

                Tuples.of("21-Oct-1972 20:16:27 +0000", "21 Oct 1972 20:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0100", "21 Oct 1972 19:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0200", "21 Oct 1972 18:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0300", "21 Oct 1972 17:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0400", "21 Oct 1972 16:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0500", "21 Oct 1972 15:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0600", "21 Oct 1972 14:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0700", "21 Oct 1972 13:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0800", "21 Oct 1972 12:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0900", "21 Oct 1972 11:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1000", "21 Oct 1972 10:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1100", "21 Oct 1972 09:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1200", "21 Oct 1972 08:16:27 GMT"),

                Tuples.of("21-Oct-1972 20:16:27 -0000", "21 Oct 1972 20:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0100", "21 Oct 1972 21:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0200", "21 Oct 1972 22:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0300", "21 Oct 1972 23:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0400", "22 Oct 1972 00:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0500", "22 Oct 1972 01:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0600", "22 Oct 1972 02:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0700", "22 Oct 1972 03:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0800", "22 Oct 1972 04:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0900", "22 Oct 1972 05:16:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -1000", "22 Oct 1972 06:16:27 GMT"),

                Tuples.of("21-Oct-1972 20:16:27 +0030", "21 Oct 1972 19:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0130", "21 Oct 1972 18:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0230", "21 Oct 1972 17:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0330", "21 Oct 1972 16:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0430", "21 Oct 1972 15:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0530", "21 Oct 1972 14:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0630", "21 Oct 1972 13:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0730", "21 Oct 1972 12:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0830", "21 Oct 1972 11:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +0930", "21 Oct 1972 10:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1030", "21 Oct 1972 09:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1130", "21 Oct 1972 08:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 +1230", "21 Oct 1972 07:46:27 GMT"),

                Tuples.of("21-Oct-1972 20:16:27 -0030", "21 Oct 1972 20:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0130", "21 Oct 1972 21:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0230", "21 Oct 1972 22:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0330", "21 Oct 1972 23:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0430", "22 Oct 1972 00:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0530", "22 Oct 1972 01:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0630", "22 Oct 1972 02:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0730", "22 Oct 1972 03:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0830", "22 Oct 1972 04:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -0930", "22 Oct 1972 05:46:27 GMT"),
                Tuples.of("21-Oct-1972 20:16:27 -1030", "22 Oct 1972 06:46:27 GMT"))
                .flatMap(tuple ->
                    Stream.of(
                        Locale.US,
                        Locale.FRANCE,
                        Locale.GERMANY,
                        Locale.UK,
                        Locale.CANADA,
                        Locale.JAPAN,
                        Locale.KOREA,
                        Locale.CHINA,
                        Locale.TAIWAN,
                        Locale.ITALY
                    ).map(locale -> Arguments.of(tuple.getT1(), tuple.getT2(), locale)));
    }

    @ParameterizedTest
    @MethodSource
    void decodeDateTimeWithVariousDefaultLocale(String input, String expected, Locale locale) throws Exception {
        Locale.setDefault(locale);
        LocalDateTime localDateTime = DecoderUtils.decodeDateTime(input);
        assertThat(ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("GMT"))
            .format(DateTimeFormatter.ofPattern("dd MMM YYYY HH:mm:ss VV", Locale.US)))
            .isEqualTo(expected);
    }
}
