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
package org.apache.james.protocols.pop3.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.Username;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.mailbox.Mailbox;

public class TestPassCmdHandler extends AbstractPassCmdHandler {
    private final Map<String, Mailbox> mailboxes = new HashMap<>();

    public TestPassCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    public void add(String username, Mailbox mailbox) {
        mailboxes.put(username, mailbox);
    }
    
    @Override
    protected Mailbox auth(POP3Session session, Username username, String password) throws Exception {
        return mailboxes.get(username.asString());
    }

}
