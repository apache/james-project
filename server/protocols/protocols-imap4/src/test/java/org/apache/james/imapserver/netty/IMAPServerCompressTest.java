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

package org.apache.james.imapserver.netty;

import static jakarta.mail.Folder.READ_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import jakarta.mail.FetchProfile;
import jakarta.mail.Session;
import jakarta.mail.Store;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerCompressTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerCompress.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void shouldNotThrowWhenCompressionEnabled() throws Exception {
        InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USER);
        mailboxManager.createMailbox(
            MailboxPath.inbox(USER),
            mailboxSession);
        mailboxManager.getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .appendMessage(MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession);

        Properties props = new Properties();
        props.put("mail.imap.user", USER.asString());
        props.put("mail.imap.host", "127.0.0.1");
        props.put("mail.imap.auth.mechanisms", "LOGIN");
        props.put("mail.imap.compress.enable", true);
        final Session session = Session.getInstance(props);
        final Store store = session.getStore("imap");
        store.connect("127.0.0.1", port, USER.asString(), USER_PASS);
        final FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        final IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
        inbox.open(READ_WRITE);

        inbox.getMessageByUID(1);
    }

    @Test
    void compressShouldFailWhenUnknownCompressionAlgorithm() throws Exception {
        String reply = testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS)
            .sendCommand("COMPRESS BAD");

        assertThat(reply).contains("AAAB BAD COMPRESS failed. Illegal arguments.");
    }
}
