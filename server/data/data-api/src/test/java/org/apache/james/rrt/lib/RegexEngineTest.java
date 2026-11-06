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

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexEngineTest {
    private static final String LONG_INPUT = "a".repeat(5000) + "!@example.org";

    @Test
    void configuredShouldDefaultToSafe() {
        assertThat(RegexEngine.configured()).isSameAs(RegexEngine.SAFE);
    }

    @Test
    void safeShouldReturnMatchingGroups() {
        assertThat(RegexEngine.SAFE.match("prefix_(.*)_(.*)@test", "prefix_abc_def@test"))
            .hasValueSatisfying(groups -> assertThat(groups).containsExactly("abc", "def"));
    }

    @Test
    void safeShouldMatchTheEntireInput() {
        assertThat(RegexEngine.SAFE.match("abc", "abc@test")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(a+)+$", "(a*)*b", "(a|a)*b"})
    void safeShouldNotBacktrackCatastrophically(String regex) {
        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThat(RegexEngine.SAFE.match(regex, LONG_INPUT)).isEmpty());
    }

    @Test
    void safeShouldBoundEvaluationOfLongestRegex() {
        String regex = "(a*)".repeat(RegexEngine.Safe.MAX_REGEX_LENGTH / 4);

        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThat(RegexEngine.SAFE.match(regex, LONG_INPUT)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "((a{1000}){1000}){1000}",
        "(.{0,1000}){0,1000}",
        "[a-z]{1,64}@test",
        "\\p{L}+",
        "a}"})
    void safeShouldRefuseBraces(String regex) {
        assertThatThrownBy(() -> RegexEngine.SAFE.validate(regex))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void safeMatchShouldRefuseBraces() {
        assertThatThrownBy(() -> RegexEngine.SAFE.match("((a{1000}){1000}){1000}", "abc@test"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void safeShouldRefuseParameters() {
        assertThatThrownBy(() -> RegexEngine.SAFE.validate("(.*)@(.*):${1}@tld"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void unsafeShouldAcceptParameters() {
        assertThatCode(() -> RegexEngine.UNSAFE.validate("(.*)@(.*):${1}@tld"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\nb", "a\u0000b:admin@test", "abc:admin\r\n@test"})
    void safeShouldRefuseControlCharacters(String mapping) {
        assertThatThrownBy(() -> RegexEngine.SAFE.validate(mapping))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(?!admin).*@domain.tld", "(a)\\1", "a++", "(", ".*):"})
    void safeShouldRefuseUnsupportedSyntax(String mapping) {
        assertThatThrownBy(() -> RegexEngine.SAFE.validate(mapping))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void safeShouldAcceptRegexOfMaximumLength() {
        assertThatCode(() -> RegexEngine.SAFE.validate("a".repeat(RegexEngine.Safe.MAX_REGEX_LENGTH)))
            .doesNotThrowAnyException();
    }

    @Test
    void safeShouldRefuseRegexExceedingMaximumLength() {
        assertThatThrownBy(() -> RegexEngine.SAFE.validate("a".repeat(RegexEngine.Safe.MAX_REGEX_LENGTH + 1)))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void safeShouldAcceptUsualMappings() {
        assertThatCode(() -> {
            RegexEngine.SAFE.validate("(.*)@localhost");
            RegexEngine.SAFE.validate("[a-z.]+@domain\\.tld:admin@tld");
            RegexEngine.SAFE.validate("(?i)prefix_(?<name>.*)@test:admin@test");
        }).doesNotThrowAnyException();
    }

    @Test
    void unsafeShouldSupportLookaroundAndBraces() {
        assertThat(RegexEngine.UNSAFE.match("(?!admin)([a-z]{1,64})@test", "bob@test"))
            .hasValueSatisfying(groups -> assertThat(groups).containsExactly("bob"));
    }

    @Test
    void unsafeShouldNotLimitLength() {
        assertThatCode(() -> RegexEngine.UNSAFE.validate("a".repeat(RegexEngine.Safe.MAX_REGEX_LENGTH + 1)))
            .doesNotThrowAnyException();
    }

    @Test
    void unsafeShouldRefuseInvalidSyntax() {
        assertThatThrownBy(() -> RegexEngine.UNSAFE.validate("("))
            .isInstanceOf(PatternSyntaxException.class);
    }
}
