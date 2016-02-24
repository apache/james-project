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
import java.util.Collection;

/**
 * Collects tokens.
 */
public class TokenCollector extends Tokenizer {

    /** Stores collected tokens */
    private final Collection<String> tokens;
 
    
    /**
     * Constructs a collector which collects tokens
     * into the given collection.
     * @param tokens not null
     */
    public TokenCollector(Collection<String> tokens) {
        super();
        this.tokens = tokens;
    }
    
    /**
     * Collects tokens from stream.
     * @param stream not null
     * @return this, not null
     * @throws IOException
     */
    public TokenCollector collect(Reader stream) throws IOException {
        doTokenize(stream);
        return this;
    }
 
    /**
     * Adds the token to the collection.
     */
    @Override
    protected void next(String token) {
        tokens.add(token);
    }
}
