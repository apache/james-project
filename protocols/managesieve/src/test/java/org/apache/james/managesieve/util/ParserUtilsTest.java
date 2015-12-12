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

import org.apache.james.managesieve.api.ArgumentException;
import org.junit.Test;

public class ParserUtilsTest {

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnNullInput() throws Exception {
        ParserUtils.getSize(null);
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnEmptyInput() throws Exception {
        ParserUtils.getSize("");
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnMalformedData1() throws Exception {
        ParserUtils.getSize("abc");
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnMalformedData2() throws Exception {
        ParserUtils.getSize("{ab");
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnMalformedData3() throws Exception {
        ParserUtils.getSize("{ab}");
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnMalformedData4() throws Exception {
        ParserUtils.getSize("{ab+}");
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnMalformedData5() throws Exception {
        ParserUtils.getSize("{ab125+}");
    }

    @Test
    public void getSizeShouldWork() throws Exception {
        assertThat(ParserUtils.getSize("{45+}")).isEqualTo(45);
    }

    @Test(expected = ArgumentException.class)
    public void getSizeShouldThrowOnExtraArguments() throws Exception {
        ParserUtils.getSize("{45+} extra");
    }

    @Test
    public void unquoteShouldReturnNullOnNullInput() {
        assertThat(ParserUtils.unquote(null)).isEqualTo(null);
    }

    @Test
    public void unquoteShouldReturnEmptyStringOnEmptyInput() {
        assertThat(ParserUtils.unquote("")).isEqualTo("");
    }

    @Test
    public void unquoteShouldNotUnquoteUnquotedQuotes() {
        assertThat(ParserUtils.unquote("a")).isEqualTo("a");
    }

    @Test
    public void unquoteShouldNotUnquoteNonStartingQuotes() {
        assertThat(ParserUtils.unquote("a\"")).isEqualTo("a\"");
    }

    @Test
    public void unquoteShouldNotUnquoteNonEndingQuotes() {
        assertThat(ParserUtils.unquote("\"a")).isEqualTo("\"a");
    }

    @Test
    public void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString() {
        assertThat(ParserUtils.unquote("a\"b\"c")).isEqualTo("a\"b\"c");
    }

    @Test
    public void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString1() {
        assertThat(ParserUtils.unquote("\"b\"c")).isEqualTo("\"b\"c");
    }

    @Test
    public void unquoteShouldNotUnquoteQuotesThatDoNotEnglobeWallString2() {
        assertThat(ParserUtils.unquote("a\"b\"")).isEqualTo("a\"b\"");
    }

    @Test
    public void unquoteShouldWorkWithDoubleQuote() {
        assertThat(ParserUtils.unquote("\"a\"")).isEqualTo("a");
    }

    @Test
    public void unquoteShouldNotUnquoteNonTrimmedData() {
        assertThat(ParserUtils.unquote(" \"a\"")).isEqualTo(" \"a\"");
    }

    @Test
    public void unquoteShouldManageSingleQuotes() {
        assertThat(ParserUtils.unquote("a'")).isEqualTo("a'");
    }

    @Test
    public void unquoteShouldManageSingleQuotes1() {
        assertThat(ParserUtils.unquote("'a")).isEqualTo("'a");
    }

    @Test
    public void unquoteShouldManageSingleQuotes2() {
        assertThat(ParserUtils.unquote("a'b")).isEqualTo("a'b");
    }

    @Test
    public void unquoteShouldWorkWithSingleQuotes() {
        assertThat(ParserUtils.unquote("'a'")).isEqualTo("a");
    }
}
