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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.mailet.MailetException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PatternExtractorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private PatternExtractor testee;

    @Before
    public void setup() {
        testee = new PatternExtractor();
    }

    @Test
    public void getPatternsFromStringShouldReturnValuesWhenMultiplePatterns() throws Exception {
        List<ReplacingPattern> patternsFromString = testee.getPatternsFromString("/test/TEST/i/,/a/e//,/o/o/ir/");

        assertThat(patternsFromString).containsOnly(
                new ReplacingPattern(Pattern.compile("test"), false, "TEST"),
                new ReplacingPattern(Pattern.compile("a"), false, "e"),
                new ReplacingPattern(Pattern.compile("o"), true, "o"));
    }

    @Test
    public void getPatternsFromFileListShouldReturnValuesWhenMultiplePatterns() throws Exception {
        List<ReplacingPattern> patternsFromFileList = testee.getPatternsFromFileList("#/org/apache/james/mailet/standard/mailets/replaceSubject.patterns");

        assertThat(patternsFromFileList).containsOnly(
                new ReplacingPattern(Pattern.compile("re:[ ]*"), false, "Re: "),
                new ReplacingPattern(Pattern.compile("ri:[ ]*"), false, "Re: "),
                new ReplacingPattern(Pattern.compile("r:[ ]*"), false, "Re: "));
    }

    @Test
    public void getPatternsFromStringShouldThrowWhenPatternIsLessThanTwoCharacters() throws Exception {
        expectedException.expect(MailetException.class);

        testee.getPatternsFromString("a");
    }

    @Test
    public void getPatternsFromStringShouldThrowWhenPatternDoesNotStartWithSlash() throws Exception {
        expectedException.expect(MailetException.class);

        testee.getPatternsFromString("abc/");
    }

    @Test
    public void getPatternsFromStringShouldThrowWhenPatternDoesNotEndWithSlash() throws Exception {
        expectedException.expect(MailetException.class);

        testee.getPatternsFromString("/abc");
    }

    @Test
    public void serviceShouldUnescapeCarriageReturn() throws Exception {
        List<ReplacingPattern> patternsFromString = testee.getPatternsFromString("/a/\\\\r/i/");
        assertThat(patternsFromString).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\r"));
    }

    @Test
    public void serviceShouldUnescapeLineBreak() throws Exception {
        List<ReplacingPattern> patternsFromString = testee.getPatternsFromString("/a/\\\\n/i/");
        assertThat(patternsFromString).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\n"));
    }

    @Test
    public void serviceShouldUnescapeTabReturn() throws Exception {
        List<ReplacingPattern> patternsFromString = testee.getPatternsFromString("/a/\\\\t/i/");
        assertThat(patternsFromString).containsOnly(new ReplacingPattern(Pattern.compile("a"), false, "\\\t"));
    }
}
