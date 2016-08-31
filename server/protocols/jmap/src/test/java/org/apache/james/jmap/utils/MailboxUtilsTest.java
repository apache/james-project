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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailboxUtilsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxUtilsTest.class);

    private MailboxManager mailboxManager;
    private MailboxMapperFactory mailboxMapperFactory;
    private MailboxSession mailboxSession;
    private String user;
    private MailboxUtils sut;

    @Before
    public void setup() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(inMemoryIntegrationResources.createGroupMembershipResolver());
        mailboxMapperFactory = new InMemoryMailboxSessionMapperFactory();
        user = "user@domain.org";
        mailboxSession = mailboxManager.login(user, "pass", LOGGER);
        sut = new MailboxUtils(mailboxManager);
    }
    @Test
    public void hasChildrenShouldReturnFalseWhenNoChild() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId();

        assertThat(sut.hasChildren(mailboxId, mailboxSession)).isFalse();
    }

    @Test
    public void hasChildrenShouldReturnTrueWhenHasAChild() throws Exception {
        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(parentMailboxPath)
                .getMailboxId();

        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox.myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        assertThat(sut.hasChildren(parentId, mailboxSession)).isTrue();
    }
}
