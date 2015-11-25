package org.apache.james.managesieve.mock;

import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;

import java.util.Arrays;
import java.util.List;

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

/**
 * <code>MockSieveParser</code>
 */
public class MockSieveParser implements SieveParser {
    
    private List<String> _extensions = null;

    public MockSieveParser() {
        super();
    }

    public List<String> getExtensions() {
        return _extensions;
    }
    
    public void setExtensions(List<String> extensions) {
        _extensions = extensions;
    }

    public List<String> parse(String content) throws SyntaxException {
        if (content.equals("SyntaxException"))
        {
            throw new SyntaxException("Ouch!");
        }
        return Arrays.asList("warning1", "warning2");
    }

}
