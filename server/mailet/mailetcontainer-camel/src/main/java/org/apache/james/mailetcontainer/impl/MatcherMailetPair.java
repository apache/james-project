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
package org.apache.james.mailetcontainer.impl;

import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;

/**
 * A pair of {@link Matcher} and {@link Mailet}
 */
public class MatcherMailetPair {
    private final Matcher matcher;
    private final Mailet mailet;

    public MatcherMailetPair(Matcher matcher, Mailet mailet) {
        this.matcher = matcher;
        this.mailet = mailet;
    }

    /**
     * Return the {@link Matcher} of this pair
     * 
     * @return matcher
     */
    public Matcher getMatcher() {
        return matcher;
    }

    /**
     * Return the {@link Mailet} of this pair
     * 
     * @return mailet
     */
    public Mailet getMailet() {
        return mailet;
    }

    public String getOnMatchException() {
        return mailet.getMailetConfig()
            .getInitParameter("onMatchException");
    }

}
