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

package org.apache.james.mpt.imapmailbox.cyrus;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.imapmailbox.suite.ACLIntegration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class CyrusACLIntegration extends ACLIntegration {

    private ImapHostSystem system;
    private GrantRightsOnHost grantRightsOnHost;
    private MailboxMessageAppender mailboxMessageAppender;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new CyrusMailboxTestModule());
        system = injector.getInstance(ImapHostSystem.class);
        grantRightsOnHost = injector.getInstance(GrantRightsOnHost.class);
        mailboxMessageAppender = injector.getInstance(MailboxMessageAppender.class);
        system.beforeTest();
        super.setUp();
    }
    
    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @AfterEach
    public void tearDown() throws Exception {
        system.afterTest();
    }

    @Override
    protected GrantRightsOnHost createGrantRightsOnHost() {
        return grantRightsOnHost;
    }

    @Override
    protected MailboxMessageAppender createMailboxMessageAppender() {
        return mailboxMessageAppender;
    }
    
}
