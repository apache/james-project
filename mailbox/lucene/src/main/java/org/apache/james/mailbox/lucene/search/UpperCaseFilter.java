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

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Normalizes token text to upper case.
 */
public final class UpperCaseFilter extends TokenFilter {
    private CharTermAttribute termAtt;

    public UpperCaseFilter(TokenStream in) {
        super(in);
        termAtt = addAttribute(CharTermAttribute.class);
    }


    @Override
    public final boolean incrementToken() throws IOException {
        if (input.incrementToken()) {

            final char[] buffer = termAtt.buffer();
            final int length = termAtt.length();
            for (int i = 0; i < length; i++)
                buffer[i] = Character.toUpperCase(buffer[i]);

            return true;
        } else
            return false;
    }
}
