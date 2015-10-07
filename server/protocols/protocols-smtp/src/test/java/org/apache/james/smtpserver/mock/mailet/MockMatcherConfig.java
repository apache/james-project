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
package org.apache.james.smtpserver.mock.mailet;

import org.apache.mailet.MailetContext;
import org.apache.mailet.MatcherConfig;

/**
 * MatcherConfig
 */
public class MockMatcherConfig implements MatcherConfig {

    private final String matcherName;
    private final MailetContext mc;

    public MockMatcherConfig(String matcherName, MailetContext mc) {
        super();
        this.matcherName = matcherName;
        this.mc = mc;
    }

    @Override
    public String getCondition() {
        if (matcherName.contains("=")) {
            return matcherName.substring(getMatcherName().length() + 1);
        } else {
            return null;
        }
    }

    @Override
    public MailetContext getMailetContext() {
        return mc;
    }

    @Override
    public String getMatcherName() {
        if (matcherName.contains("=")) {
            return matcherName.split("=")[0];
        } else {
            return matcherName;
        }
    }
}
