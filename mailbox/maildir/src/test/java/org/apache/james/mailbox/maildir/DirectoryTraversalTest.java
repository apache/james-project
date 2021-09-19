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
package org.apache.james.mailbox.maildir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.UUID;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryTraversalTest {
    StoreMailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        mailboxManager = MaildirMailboxManagerProvider.createMailboxManager("/%fulluser",
            new File(System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID()));
    }

    @Test
    void directoryTraversalUsingUsernameFails() {
        MailboxSession session = mailboxManager.createSystemSession(Username.of("../bob"));

        assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.inbox(session), session))
            .hasMessageContaining("jail breaks out of");
    }

    @Test
    void directoryTraversalUsingMailboxName1Fails() {
        MailboxSession session = mailboxManager.createSystemSession(Username.of("bob"));

        assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(session.getUser(), "./alice/box2"), session))
            .isNotNull();
    }

    @Test
    void directoryTraversalUsingMailboxName2Fails() {
        MailboxSession session = mailboxManager.createSystemSession(Username.of("bob"));

        assertThatThrownBy(() -> mailboxManager.createMailbox(MailboxPath.forUser(session.getUser(), "alice/../box2"), session))
            .isNotNull();
    }
}
