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
import org.apache.lucene.analysis.shingle.ShingleFilterFactory;

/**
 * This {@link Analyzer} is not 100% conform with RFC3501 but does
 * most times exactly what the user would expect.
 */
public final class LenientImapSearchAnalyzer {

    public static final int DEFAULT_MAX_TOKEN_LENGTH = 4;

    static Analyzer getAnalyzer() {
        return getAnalyzer(DEFAULT_MAX_TOKEN_LENGTH);
    }

    static Analyzer getAnalyzer(int maxTokenLength) {
        try {
            return CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.NAME)
                    .addTokenFilter(UpperCaseFilterFactory.NAME)
                    .addTokenFilter(ShingleFilterFactory.NAME,
                            "minShingleSize", "2", "maxShingleSize", String.valueOf(maxTokenLength),
                            "outputUnigrams", "true",
                            "outputUnigramsIfNoShingles", "false",
                            "tokenSeparator", " ", "fillerToken", "_"
                    ).build();
        } catch (IOException e) {
            throw new RuntimeException("Can't instantiate custom (lenient) analyzer", e);
        }
    }
}
