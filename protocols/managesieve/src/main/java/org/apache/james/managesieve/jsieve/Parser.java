/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.jsieve;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.parser.generated.ParseException;

public class Parser implements SieveParser {
    
    private static final List<String> EMPTY_WARNINGS = new ArrayList<>(0);
    
    private final SieveFactory sieveFactory;

    public Parser(SieveFactory sieveFactory) {
        this.sieveFactory = sieveFactory;
    }

    public Parser() throws Exception {
        this(new ConfigurationManager());
    }

    public Parser(ConfigurationManager manager) {
        this(manager.build());
    }

    @Override
    public List<String> getExtensions() {
        return sieveFactory.getExtensions();
    }

    @Override
    public List<String> parse(String content) throws SyntaxException {
        try {
            sieveFactory.parse(new ByteArrayInputStream(content.getBytes()));
        } catch (ParseException ex) {
            throw new SyntaxException(ex);
        }
        return EMPTY_WARNINGS;
    }

}
