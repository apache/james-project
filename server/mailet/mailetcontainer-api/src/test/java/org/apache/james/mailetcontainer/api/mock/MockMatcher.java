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
package org.apache.james.mailetcontainer.api.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;

public class MockMatcher implements Matcher {

    private int matchCount = 0;
    private MatcherConfig config;

    @Override
    public void destroy() {
    }

    @Override
    public MatcherConfig getMatcherConfig() {
        return config;
    }

    @Override
    public String getMatcherInfo() {
        return getClass().getName();
    }

    @Override
    public void init(MatcherConfig config) {
        this.config = config;
        matchCount = Integer.parseInt(config.getCondition());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        List<MailAddress> match = new ArrayList<>();

        Iterator<MailAddress> rcpts = mail.getRecipients().iterator();
        while (rcpts.hasNext() && match.size() < matchCount) {
            MailAddress addr = rcpts.next();
            match.add(addr);

        }
        if (match.isEmpty()) {
            return null;
        }
        return match;
    }
}
