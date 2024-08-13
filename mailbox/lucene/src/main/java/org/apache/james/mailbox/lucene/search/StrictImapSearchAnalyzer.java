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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.UpperCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.ngram.NGramFilterFactory;

/**
 * {@link Analyzer} which match substrings. This is needed because of RFC 3501.
 * <p>
 * From RFC:
 * <p>
 * In all search keys that use strings, a message matches the key if
 * the string is a substring of the field.  The matching is
 * case-insensitive.
 */
public final class StrictImapSearchAnalyzer {
    static Analyzer getAnalyzer() {
        return getAnalyzer(3, 40);
    }

    static Analyzer getAnalyzer(int minTokenLength, int maxTokenLength) {
        try {
            return CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.NAME)
                    .addTokenFilter(UpperCaseFilterFactory.NAME)
                    .addTokenFilter(NGramFilterFactory.NAME,
                            "minGramSize", String.valueOf(minTokenLength),
                            "maxGramSize", String.valueOf(maxTokenLength),
                            "preserveOriginal", "true"
                    ).build();
        } catch (IOException e) {
            throw new RuntimeException("Can't instantiate custom (strict) analyzer", e);
        }
    }
}