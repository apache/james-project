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

import java.io.Reader;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.util.Version;

/**
 * This {@link Analyzer} is not 100% conform with RFC3501 but does
 * most times exactly what the user would expect. 
 *
 */
public final class LenientImapSearchAnalyzer extends Analyzer{

    public final static int DEFAULT_MAX_TOKEN_LENGTH = 4;
    
    private final int maxTokenLength;
    

    public LenientImapSearchAnalyzer(int maxTokenLength) {
        this.maxTokenLength = maxTokenLength;
    }
    
    public LenientImapSearchAnalyzer() {
        this(DEFAULT_MAX_TOKEN_LENGTH);
    }
    
    @Override
    public TokenStream tokenStream(String arg0, Reader reader) {
        return new ShingleFilter(new UpperCaseFilter(new WhitespaceTokenizer(Version.LUCENE_31, reader)), 2, maxTokenLength);
    }
}
