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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.Sets;

class DecoderUtilsTest {

    static final String EXTENSION_FLAG = "\\Extension";
    static final String A_CUSTOM_FLAG = "Another";

    @Test
    void setFlagShouldRejectRecentFlag() {
        assertThatThrownBy(() -> DecoderUtils.setFlag("\\Recent", new Flags()))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void customFlagShouldBeAdded() throws Exception {
        Flags flags = new Flags();
        DecoderUtils.setFlag(A_CUSTOM_FLAG, flags);
        assertThat(flags.contains(A_CUSTOM_FLAG))
            .describedAs("Unknown flags should be added")
            .isTrue();
    }

    @Test
    void extensionFlagShouldBeAdded() throws Exception {
        Flags flags = new Flags();
        DecoderUtils.setFlag(EXTENSION_FLAG, flags);
        assertThat(flags.contains(EXTENSION_FLAG))
            .describedAs("Extension flags should be added")
            .isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"",
        "This is a string long enough to be too big", 
        "1", 
        "12", 
        "123", 
        "1234", 
        "12345", 
        "123456", 
        "1234567", 
        "12345678", 
        "123456789", 
        "1234567890", 
        "12345678901", 
        "123456789012", 
        "1234567890123", 
        "12345678901234", 
        "123456789012345", 
        "1234567890123456", 
        "12345678901234567", 
        "123456789012345678", 
        "1234567890123456789", 
        "12345678901234567890", 
        "123456789012345678901", 
        "1234567890123456789012", 
        "12345678901234567890123", 
        "123456789012345678901234", 
        "1234567890123456789012345", 
        "12345678901234567890123456", 
        "123456789012345678901234567"
    })
    void decodeShouldThrowOnBadDateTime(String datetime) {
        assertThatThrownBy(() -> DecoderUtils.decodeDateTime(datetime))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeDateTimeShouldThrowOnNull() {
        assertThatThrownBy(() -> DecoderUtils.decodeDateTime(null))
            .isInstanceOf(DecodingException.class);
    }

    static Stream<Arguments> nominalDecodeDateTime() {
        return Stream.of(
        Arguments.of("21-Oct-1972 20:00:00 +0000", "21 Oct 1972 20:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0100", "21 Oct 1972 19:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0200", "21 Oct 1972 18:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0300", "21 Oct 1972 17:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0400", "21 Oct 1972 16:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0500", "21 Oct 1972 15:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0600", "21 Oct 1972 14:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0700", "21 Oct 1972 13:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0800", "21 Oct 1972 12:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +0900", "21 Oct 1972 11:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +1000", "21 Oct 1972 10:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +1100", "21 Oct 1972 09:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 +1200", "21 Oct 1972 08:00:00 GMT"),

        Arguments.of("21-Oct-1972 20:00:00 +1000", "21 Oct 1972 10:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0100", "21 Oct 1972 21:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0200", "21 Oct 1972 22:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0300", "21 Oct 1972 23:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0400", "22 Oct 1972 00:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0500", "22 Oct 1972 01:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0600", "22 Oct 1972 02:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0700", "22 Oct 1972 03:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0800", "22 Oct 1972 04:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0900", "22 Oct 1972 05:00:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -1000", "22 Oct 1972 06:00:00 GMT"),

        Arguments.of("21-Oct-1972 20:00:00 +0030", "21 Oct 1972 19:30:00 GMT"),
        Arguments.of("21-Oct-1972 20:00:00 -0030", "21 Oct 1972 20:30:00 GMT"),

        Arguments.of("21-Oct-1972 20:00:00 -1000", "22 Oct 1972 06:00:00 GMT"),
        Arguments.of("21-Oct-1972 06:00:00 +1000", "20 Oct 1972 20:00:00 GMT"),
        Arguments.of("21-Oct-1972 06:00:00 -1000", "21 Oct 1972 16:00:00 GMT"));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeDateTime(String input, String parsed) throws DecodingException {
        LocalDateTime localDateTime = DecoderUtils.decodeDateTime(input);
        assertThat(ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("GMT"))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss VV", Locale.US)))
            .isEqualTo(parsed);
    }

    @Test
    void decodeDatetimeShouldAllowAppleMailPrependsZeroNotSpace() throws Exception {
        LocalDateTime localDateTime = DecoderUtils.decodeDateTime("09-Apr-2008 15:17:51 +0200");
        assertThat(ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("GMT"))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss VV", Locale.US)))
            .isEqualTo("09 Apr 2008 13:17:51 GMT");
    }

    static Stream<Arguments> nominalDecodeDateTimeWithTimezone() {
        return Sets.cartesianProduct(
            Stream.of(
                "GMT+0",
                "GMT+1",
                "GMT-1",
                "GMT+2",
                "GMT-2",
                "GMT+3",
                "GMT-3",
                "GMT+11",
                "GMT-11",
                "GMT+1030",
                "GMT-1030",
                "GMT+0045",
                "GMT-0045")
            .map(TimeZone::getTimeZone)
            .collect(Collectors.toSet()),
            LongStream.of(
                10000000,
                100000000,
                1000000000,
                10000000000L,
                100000000000L,
                1000000000000L,
                1194168899658L,
                1912093499271L,
                1526720308423L,
                1487487260757L,
                1584040720026L,
                1983293490921L,
                1179806572669L,
                1194038035064L,
                1057865248366L,
                1052797936633L,
                1075268253439L,
                1033938440306L,
                1031614051298L,
                1059929345305L,
                1162582627756L,
                1185747232134L,
                1151301821303L,
                1116091684805L,
                1159599194961L,
                1222523245646L,
                1219556266559L,
                1290015730272L,
                1221694598854L,
                1212132783343L,
                1221761134897L,
                1270941981377L,
                1224491731327L,
                1268571556436L,
                1246838821081L,
                1226795970848L,
                1260254185119L
            ).mapToObj(Instant::ofEpochMilli)
            .collect(Collectors.toSet())
        ).stream()
            .map(list -> Arguments.of(list.toArray()));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeDateTimeWithTimezone(TimeZone zone, Instant date) throws Exception {
        FastDateFormat format = FastDateFormat.getInstance("dd-MMM-yyyy HH:mm:ss Z", zone, Locale.US);
        String in = format.format(Date.from(date));
        LocalDateTime decodedDate = DecoderUtils.decodeDateTime(in);
        assertThat(decodedDate.atZone(ZoneId.systemDefault())).describedAs("Round trip").isEqualToIgnoringNanos(ZonedDateTime.ofInstant(date, zone.toZoneId()));
    }

    static Stream<Arguments> nominalDecodeDigit() {
        return Stream.of(
            Arguments.of('0', 0),
            Arguments.of('1', 1),
            Arguments.of('2', 2),
            Arguments.of('3', 3),
            Arguments.of('4', 4),
            Arguments.of('5', 5),
            Arguments.of('6', 6),
            Arguments.of('7', 7),
            Arguments.of('8', 8),
            Arguments.of('9', 9));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeDigit(char digit, int expected) throws DecodingException {
        assertThat(DecoderUtils.decodeDigit(digit)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(chars = {'/', ':'})
    void decodeDigitShouldThrowOnNonDigit(char c) {
        assertThatThrownBy(() -> DecoderUtils.decodeDigit(c))
            .isInstanceOf(DecodingException.class);
    }

    static Stream<Arguments> nominalDecodeMonth() {
        return Stream.of(
            Arguments.of('J', 'A', 'N', Calendar.JANUARY),
            Arguments.of('j', 'a', 'n', Calendar.JANUARY),
            Arguments.of('F', 'E', 'B', Calendar.FEBRUARY),
            Arguments.of('f', 'e', 'b', Calendar.FEBRUARY),
            Arguments.of('M', 'A', 'R', Calendar.MARCH),
            Arguments.of('m', 'a', 'r', Calendar.MARCH),
            Arguments.of('A', 'P', 'R', Calendar.APRIL),
            Arguments.of('a', 'p', 'r', Calendar.APRIL),
            Arguments.of('M', 'A', 'Y', Calendar.MAY),
            Arguments.of('m', 'a', 'y', Calendar.MAY),
            Arguments.of('J', 'U', 'N', Calendar.JUNE),
            Arguments.of('j', 'u', 'n', Calendar.JUNE),
            Arguments.of('J', 'U', 'L', Calendar.JULY),
            Arguments.of('j', 'u', 'l', Calendar.JULY),
            Arguments.of('A', 'U', 'G', Calendar.AUGUST),
            Arguments.of('a', 'u', 'g', Calendar.AUGUST),
            Arguments.of('S', 'E', 'P', Calendar.SEPTEMBER),
            Arguments.of('s', 'e', 'p', Calendar.SEPTEMBER),
            Arguments.of('O', 'C', 'T', Calendar.OCTOBER),
            Arguments.of('o', 'c', 't', Calendar.OCTOBER),
            Arguments.of('N', 'O', 'V', Calendar.NOVEMBER),
            Arguments.of('n', 'o', 'v', Calendar.NOVEMBER),
            Arguments.of('D', 'E', 'C', Calendar.DECEMBER),
            Arguments.of('d', 'e', 'c', Calendar.DECEMBER));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeMonth(char c1, char c2, char c3, int expected) throws Exception {
        assertThat(DecoderUtils.decodeMonth(c1, c2, c3)).isEqualTo(expected);
    }


    static Stream<Arguments> decodeMonthShouldRejectBogusMonths() {
        return Stream.of(
            Arguments.of('N', 'O', 'C'),
            Arguments.of('A', 'N', 'T'),
            Arguments.of('Z', 'Z', 'Z'),
            Arguments.of('S', 'I', 'P'),
            Arguments.of('D', 'E', 'P'));
    }

    @ParameterizedTest
    @MethodSource
    void decodeMonthShouldRejectBogusMonths(char one, char two, char three) {
        assertThatThrownBy(() -> DecoderUtils.decodeMonth(one, two, three))
            .isInstanceOf(DecodingException.class);
    }

    static Stream<Arguments> nominalDecodeYear() {
        return Stream.of(
            Arguments.of('1', '9', '9', '9', 1999),
            Arguments.of('0', '7', '4', '7', 747),
            Arguments.of('2', '5', '2', '5', 2525),
            Arguments.of('5', '6', '7', '8', 5678),
            Arguments.of('2', '4', '5', '3', 2453),
            Arguments.of('2', '0', '0', '0', 2000),
            Arguments.of('2', '0', '0', '7', 2007),
            Arguments.of('2', '0', '0', '8', 2008),
            Arguments.of('2', '0', '1', '0', 2010),
            Arguments.of('2', '0', '2', '0', 2020));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeYear(char c1, char c2, char c3, char c4, int exptected) throws Exception {
        assertThat(DecoderUtils.decodeYear(c1, c2, c3, c4)).isEqualTo(exptected);
    }

    static Stream<Arguments> decodeShouldRejectBogusYear() {
        return Stream.of(
            Arguments.of('D', '0', '2', '3'),
            Arguments.of('1', 'A', '2', '3'),
            Arguments.of('1', '5', 'B', '3'),
            Arguments.of('9', '8', '2', 'C'),
            Arguments.of('S', 'A', 'F', 'd'));
    }

    @ParameterizedTest
    @MethodSource
    void decodeShouldRejectBogusYear(char one, char two, char three, char four) {
        assertThatThrownBy(() -> DecoderUtils.decodeYear(one, two, three, four))
            .isInstanceOf(DecodingException.class);
    }

    static Stream<Arguments> nominalDecodeZone() {
        return Stream.of(
            Arguments.of('+', '0', '0', '0', '0', 0),
            Arguments.of('+', '0', '1', '0', '0', 100),
            Arguments.of('+', '0', '2', '0', '0', 200),
            Arguments.of('+', '0', '3', '0', '0', 300),
            Arguments.of('+', '0', '4', '0', '0', 400),
            Arguments.of('+', '0', '5', '0', '0', 500),
            Arguments.of('+', '0', '6', '0', '0', 600),
            Arguments.of('+', '0', '7', '0', '0', 700),
            Arguments.of('+', '0', '8', '0', '0', 800),
            Arguments.of('+', '0', '9', '0', '0', 900),
            Arguments.of('+', '1', '0', '0', '0', 1000),
            Arguments.of('+', '1', '1', '0', '0', 1100),
            Arguments.of('+', '1', '2', '0', '0', 1200),
            Arguments.of('+', '0', '0', '3', '0', 30),
            Arguments.of('+', '0', '1', '3', '0', 130),
            Arguments.of('+', '0', '2', '3', '0', 230),
            Arguments.of('+', '0', '3', '3', '0', 330),
            Arguments.of('+', '0', '4', '3', '0', 430),
            Arguments.of('+', '0', '5', '3', '0', 530),
            Arguments.of('+', '0', '6', '3', '0', 630),
            Arguments.of('+', '0', '7', '3', '0', 730),
            Arguments.of('+', '0', '8', '3', '0', 830),
            Arguments.of('+', '0', '9', '3', '0', 930),
            Arguments.of('+', '1', '0', '3', '0', 1030),
            Arguments.of('+', '1', '1', '3', '0', 1130),
            Arguments.of('+', '1', '1', '1', '1', 1111),
            Arguments.of('-', '0', '0', '0', '0', 0),
            Arguments.of('-', '0', '1', '0', '0', -100),
            Arguments.of('-', '0', '2', '0', '0', -200),
            Arguments.of('-', '0', '3', '0', '0', -300),
            Arguments.of('-', '0', '4', '0', '0', -400),
            Arguments.of('-', '0', '5', '0', '0', -500),
            Arguments.of('-', '0', '6', '0', '0', -600),
            Arguments.of('-', '0', '7', '0', '0', -700),
            Arguments.of('-', '0', '8', '0', '0', -800),
            Arguments.of('-', '0', '9', '0', '0', -900),
            Arguments.of('-', '1', '0', '0', '0', -1000),
            Arguments.of('-', '1', '1', '0', '0', -1100),
            Arguments.of('-', '1', '2', '0', '0', -1200),
            Arguments.of('-', '0', '0', '3', '0', -30),
            Arguments.of('-', '0', '1', '3', '0', -130),
            Arguments.of('-', '0', '2', '3', '0', -230),
            Arguments.of('-', '0', '3', '3', '0', -330),
            Arguments.of('-', '0', '4', '3', '0', -430),
            Arguments.of('-', '0', '5', '3', '0', -530),
            Arguments.of('-', '0', '6', '3', '0', -630),
            Arguments.of('-', '0', '7', '3', '0', -730),
            Arguments.of('-', '0', '8', '3', '0', -830),
            Arguments.of('-', '0', '9', '3', '0', -930),
            Arguments.of('-', '1', '0', '3', '0', -1030),
            Arguments.of('-', '1', '1', '3', '0', -1130),
            Arguments.of('-', '1', '1', '1', '1', -1111));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeZone(char c1, char c2, char c3, char c4, char c5, int exptected) throws Exception {
        assertThat(DecoderUtils.decodeZone(c1, c2, c3, c4, c5)).isEqualTo(exptected);
    }

    static Stream<Arguments> decodeShouldRejectBogusZone() {
        return Stream.of(
            Arguments.of(' ', '0', '0', '0', '0'),
            Arguments.of(' ', 'G', 'M', 'T', ' '),
            Arguments.of('D', 'A', 'N', 'G', '!'),
            Arguments.of('+', 'a', '0', '0', '0'),
            Arguments.of('+', '0', 'b', '0', '0'),
            Arguments.of('+', '0', '0', 'c', '0'),
            Arguments.of('+', '0', '0', '0', 'd'),
            Arguments.of('-', 'a', '0', '0', '0'),
            Arguments.of('-', '0', 'b', '0', '0'),
            Arguments.of('-', '0', '0', 'c', '0'),
            Arguments.of('-', '0', '0', '0', 'd'));
    }

    @ParameterizedTest
    @MethodSource
    void decodeShouldRejectBogusZone(char c1, char c2, char c3, char c4, char c5) {
        assertThatThrownBy(() -> DecoderUtils.decodeZone(c1, c2, c3, c4, c5))
            .isInstanceOf(DecodingException.class);
    }

    @ParameterizedTest
    @ValueSource(chars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'})
    void nominalSimpleDigit(char c) {
        assertThat(DecoderUtils.isSimpleDigit(c)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(chars = {'/', '.', '-', '+', ',', '*', ':', ';', '<', '=', '>', 'A', 'B'})
    void simpleDigitShouldReturnFalseOnNonDigit(char c) {
        assertThat(DecoderUtils.isSimpleDigit(c)).isFalse();
    }

    static Stream<Arguments> nominalDecodeNumber() {
        return Stream.of(
            Arguments.of('0', '1', 1),
            Arguments.of('0', '2', 2),
            Arguments.of('0', '3', 3),
            Arguments.of('0', '4', 4),
            Arguments.of('0', '5', 5),
            Arguments.of('0', '6', 6),
            Arguments.of('0', '7', 7),
            Arguments.of('0', '8', 8),
            Arguments.of('0', '9', 9),
            Arguments.of('1', '9', 19),
            Arguments.of('2', '8', 28),
            Arguments.of('3', '7', 37),
            Arguments.of('4', '6', 46),
            Arguments.of('5', '5', 55),
            Arguments.of('6', '4', 64),
            Arguments.of('7', '3', 73),
            Arguments.of('8', '2', 82),
            Arguments.of('9', '1', 91));
    }

    @ParameterizedTest
    @MethodSource
    void nominalDecodeNumber(char c1, char c2, int expected) throws DecodingException {
        assertThat(DecoderUtils.decodeNumber(c1, c2)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A1", "1A", "AA"})
    void decodeShouldRejectBogusNumbers(String number) {
        assertThatThrownBy(() -> DecoderUtils.decodeNumber(number.charAt(0), number.charAt(1)))
            .isInstanceOf(DecodingException.class);
    }
}
