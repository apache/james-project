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

package org.apache.james.transport.mailets;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A data helper bean holding a lists of patterns, substitutions and flags
 */
public class PatternList {

    private List<Pattern> patterns = new ArrayList<Pattern>();
    private List<String> substitutions = new ArrayList<String>();
    private List<Integer> flags = new ArrayList<Integer>();

    public List<Pattern> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public List<String> getSubstitutions() {
        return substitutions;
    }

    public void setSubstitutions(List<String> substitutions) {
        this.substitutions = substitutions;
    }

    public List<Integer> getFlags() {
        return flags;
    }

    public void setFlags(List<Integer> flags) {
        this.flags = flags;
    }
}