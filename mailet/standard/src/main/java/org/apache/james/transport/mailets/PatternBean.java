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

import java.util.regex.Pattern;

/**
 * A data helper bean holding patterns, substitutions and flags
 */
public class PatternBean {

    private Pattern patterns = null;
    private String substitutions = null;
    private Integer flag = null;

    public PatternBean(Pattern patterns, String substitutions, Integer flag) {
        super();
        this.patterns = patterns;
        this.substitutions = substitutions;
        this.flag = flag;
    }

    public Pattern getPatterns() {
        return patterns;
    }

    public void setPatterns(Pattern patterns) {
        this.patterns = patterns;
    }

    public String getSubstitutions() {
        return substitutions;
    }

    public void setSubstitutions(String substitutions) {
        this.substitutions = substitutions;
    }

    public Integer getFlag() {
        return flag;
    }

    public void setFlag(Integer flag) {
        this.flag = flag;
    }

}