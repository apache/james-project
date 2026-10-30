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

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.UpperCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.shingle.ShingleFilterFactory;

/**
 * This {@link Analyzer} is not 100% conform with RFC3501 but does
 * most times exactly what the user would expect.
 *
 * <p>The text is split on whitespace and upper-cased, then indexed as words and as shingles of up to
 * {@link #DEFAULT_MAX_TOKEN_LENGTH} words. Before that, punctuation at the start and at the end of each
 * word is removed (see {@link #removePunctuationAroundWords(String)}), so {@code <Team sync>} is indexed
 * as {@code TEAM} and {@code SYNC}. Punctuation inside a word, as in {@code bob@example.com}, is kept.</p>
 */
public final class LenientImapSearchAnalyzer {

    public static final int DEFAULT_MAX_TOKEN_LENGTH = 4;

    /**
     * Runs of characters that are neither letters, digits nor whitespace, at the start or at the end of a
     * whitespace separated word. A word made only of such characters, like {@code -}, is removed entirely.
     */
    private static final String PUNCTUATION_AROUND_WORDS = "(?<!\\S)[^\\p{L}\\p{N}\\s]+|[^\\p{L}\\p{N}\\s]+(?!\\S)";
    private static final Pattern PUNCTUATION_AROUND_WORDS_PATTERN = Pattern.compile(PUNCTUATION_AROUND_WORDS);

    static final Analyzer INSTANCE = getAnalyzer();

    private static Analyzer getAnalyzer() {
        try {
            return CustomAnalyzer.builder()
                    .addCharFilter(PatternReplaceCharFilterFactory.NAME,
                            "pattern", PUNCTUATION_AROUND_WORDS, "replacement", "")
                    .withTokenizer(WhitespaceTokenizerFactory.NAME)
                    .addTokenFilter(UpperCaseFilterFactory.NAME)
                    .addTokenFilter(ShingleFilterFactory.NAME,
                            "minShingleSize", "2", "maxShingleSize", String.valueOf(DEFAULT_MAX_TOKEN_LENGTH),
                            "outputUnigrams", "true",
                            "outputUnigramsIfNoShingles", "false",
                            "tokenSeparator", " ", "fillerToken", "_"
                    ).build();
        } catch (IOException e) {
            // The IOException comes from the resource files.
            // If there are no resources, the CustomAnalyzer won't throw UOE, so can be warped with an AssertionError.
            throw new AssertionError("Can't instantiate custom (lenient) analyzer", e);
        }
    }

    /**
     * Apply to a searched value the punctuation removal done by this analyzer at index time.
     */
    static String removePunctuationAroundWords(String value) {
        return PUNCTUATION_AROUND_WORDS_PATTERN.matcher(value).replaceAll("");
    }
}
