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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImapRequestLineReaderTest {
    private final OutputStream outputStream = null;
    private InputStream inputStream;
    private ImapRequestStreamLineReader lineReader;

    @Test
    void nextNonSpaceCharShouldReturnTheFirstCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("anyString \n").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test
    void nextNonSpaceCharShouldIgnoreTheSpaceAndReturnTheFirstNonSpaceCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("    anyString \n").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test
    void nextNonSpaceCharShouldThrowExceptionWhenNotFound() {
        inputStream = new ByteArrayInputStream(("    ").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThatThrownBy(() -> lineReader.nextNonSpaceChar()).isInstanceOf(DecodingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1: ", ":1 ", "-:1 ", "1:1:3 ", "--0:5 ", "-00:3 "})
    void parsePartialRangeShouldRejectMalformedRanges(String range) {
        inputStream = new ByteArrayInputStream(range.getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThatThrownBy(() -> lineReader.parsePartialRange()).isInstanceOf(DecodingException.class);
    }

    private ImapRequestStreamLineReader reader(String input) {
        return new ImapRequestStreamLineReader(new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)), outputStream);
    }

    @Test
    void numberShouldBeRead() throws Exception {
        assertThat(reader("1234 ").number()).isEqualTo(1234L);
    }

    @Test
    void numberShouldAcceptLongMaxValue() throws Exception {
        assertThat(reader(Long.MAX_VALUE + " ").number()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void numberShouldRejectValuesExceedingLongMaxValue() {
        assertThatThrownBy(() -> reader("9223372036854775808 ").number())
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void numberShouldRejectValuesWhoseMultiplicationOverflows() {
        assertThatThrownBy(() -> reader("99999999999999999999 ").number())
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void numberWithManyDigitsShouldBeReadWithoutStackOverflow() throws Exception {
        assertThat(reader("0".repeat(60000) + "1 ").number()).isEqualTo(1L);
    }

    @Test
    void numberShouldStopOnClosingParenthesisWhenAsked() throws Exception {
        ImapRequestStreamLineReader reader = reader("12)");

        assertThat(reader.number(true)).isEqualTo(12L);
        assertThat(reader.nextChar()).isEqualTo(')');
    }

    @Test
    void numberShouldRejectClosingParenthesisByDefault() {
        assertThatThrownBy(() -> reader("12)").number())
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void numberShouldRejectNonDigits() {
        assertThatThrownBy(() -> reader("12a ").number())
            .isInstanceOf(DecodingException.class);
    }
}
