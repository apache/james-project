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
package org.apache.james.mailbox.lucene.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.common.collect.ImmutableList;

class LenientImapSearchAnalyzerTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "<Team|Team",
        "sync>|sync",
        "\"Hello,|Hello",
        "bob@example.com|bob@example.com",
        "<test99@localhost>|test99@localhost",
        "budget - review|budget  review",
        "user-from@domain.org|user-from@domain.org",
        "C++|C",
        "---|''",
        "(fwd)|fwd"
    })
    void removePunctuationAroundWordsShouldKeepLettersAndDigits(String value, String expected) {
        assertThat(LenientImapSearchAnalyzer.removePunctuationAroundWords(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Invitation <Team sync>|INVITATION,TEAM,SYNC,INVITATION TEAM,TEAM SYNC,INVITATION TEAM SYNC",
        "budget - review|BUDGET,REVIEW,BUDGET REVIEW"
    })
    void analyzerShouldIndexWordsWithoutSurroundingPunctuation(String value, String expectedTokens) throws IOException {
        assertThat(tokens(value)).containsExactlyInAnyOrder(expectedTokens.split(","));
    }

    private ImmutableList<String> tokens(String value) throws IOException {
        ImmutableList.Builder<String> tokens = ImmutableList.builder();
        try (TokenStream tokenStream = LenientImapSearchAnalyzer.INSTANCE.tokenStream("field", value)) {
            CharTermAttribute term = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                tokens.add(term.toString());
            }
            tokenStream.end();
        }
        return tokens.build();
    }
}
