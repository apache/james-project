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

package org.apache.james.ai.classic;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

/**
 * Counts tokens occuring in stream.
 * Totals are added to map.
 */
public class TokenCounter extends Tokenizer {

    /** Counts for token indexed by token */
    private final Map<String, Integer> countsByToken;
    
    /**
     * Constructs a token counter to update values in given map.
     * @param countsByToken counts for token indexed by token, not null 
     */
    public TokenCounter(Map<String, Integer> countsByToken) {
        super();
        this.countsByToken = countsByToken;
    }

    /**
     * Tokenizes and adds token counts to map.
     * @param stream not null
     * @return this, not null
     * @throws IOException
     */
    public TokenCounter count(Reader stream) throws IOException {
        doTokenize(stream);
        return this;
    }
    
    /**
     * Updates count for token in map.
     */
    @Override
    protected void next(String token) {
        Integer value;

        if (countsByToken.containsKey(token)) {
            value = countsByToken.get(token) + 1;
        } else {
            value = 1;
        }

        countsByToken.put(token, value);        
    }

}
