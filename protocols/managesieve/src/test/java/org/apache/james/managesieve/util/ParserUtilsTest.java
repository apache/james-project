/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.managesieve.api.ArgumentException;
import org.junit.jupiter.api.Test;

class ParserUtilsTest {
    @Test
    void getSizeShouldThrowOnNullInput() {
        assertThatThrownBy(() -> ParserUtils.getSize(null))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnEmptyInput() {
        assertThatThrownBy(() -> ParserUtils.getSize(""))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnMalformedData1()  {
        assertThatThrownBy(() -> ParserUtils.getSize("abc"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnMalformedData2() {
        assertThatThrownBy(() -> ParserUtils.getSize("{ab"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnMalformedData3() {
        assertThatThrownBy(() -> ParserUtils.getSize("{ab}"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnMalformedData4() {
        assertThatThrownBy(() -> ParserUtils.getSize("{ab+}"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldThrowOnMalformedData5() {
        assertThatThrownBy(() -> ParserUtils.getSize("{ab125+}"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void getSizeShouldWork() throws Exception {
        assertThat(ParserUtils.getSize("{45+}")).isEqualTo(45);
    }

    @Test
    void getSizeShouldThrowOnExtraArguments() {
        assertThatThrownBy(() -> ParserUtils.getSize("{45+} extra"))
            .isInstanceOf(ArgumentException.class);
    }

    @Test
    void unquoteShouldReturnNullOnNullInput() {
        assertThat(ParserUtils.unquote(null)).isEqualTo(null);
    }

    @Test
    void unquoteShouldReturnEmptyStringOnEmptyInput() {
        assertThat(ParserUtils.unquote("")).isEqualTo("");
    }

    @Test
    void unquoteShouldNotUnquoteUnquotedQuotes() {
        assertThat(ParserUtils.unquote("a")).isEqualTo("a");
    }

    @Test
    void unquoteShouldNotUnquoteNonStartingQuotes() {
        assertThat(ParserUtils.unquote("a\"")).isEqualTo("a\"");
    }

    @Test
    void unquoteShouldNotUnquoteNonEndingQuotes() {
        assertThat(ParserUtils.unquote("\"a")).isEqualTo("\"a");
    }

    @Test
    void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString() {
        assertThat(ParserUtils.unquote("a\"b\"c")).isEqualTo("a\"b\"c");
    }

    @Test
    void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString1() {
        assertThat(ParserUtils.unquote("\"b\"c")).isEqualTo("\"b\"c");
    }

    @Test
    void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString2() {
        assertThat(ParserUtils.unquote("a\"b\"")).isEqualTo("a\"b\"");
    }

    @Test
    void unquoteShouldWorkWithDoubleQuote() {
        assertThat(ParserUtils.unquote("\"a\"")).isEqualTo("a");
    }

    @Test
    void unquoteShouldNotUnquoteNonTrimmedData() {
        assertThat(ParserUtils.unquote(" \"a\"")).isEqualTo(" \"a\"");
    }

    @Test
    void unquoteShouldManageSingleQuotes() {
        assertThat(ParserUtils.unquote("a'")).isEqualTo("a'");
    }

    @Test
    void unquoteShouldManageSingleQuotes1() {
        assertThat(ParserUtils.unquote("'a")).isEqualTo("'a");
    }

    @Test
    void unquoteShouldManageSingleQuotes2() {
        assertThat(ParserUtils.unquote("a'b")).isEqualTo("a'b");
    }

    @Test
    void unquoteShouldWorkWithSingleQuotes() {
        assertThat(ParserUtils.unquote("'a'")).isEqualTo("a");
    }
}
