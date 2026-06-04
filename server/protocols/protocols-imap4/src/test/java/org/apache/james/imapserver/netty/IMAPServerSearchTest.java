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

import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.RecipientStringTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerSearchTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;
    private SocketChannel clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        port = imapServer.getListenAddresses().get(0).getPort();

        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(clientConnection);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    // Not an MPT test as ThreadId is a variable server-set and implementation specific
    @Test
    void imapSearchShouldSupportThreadId() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        MessageManager.AppendResult appendResult = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Reply-To: b@linagora.com\r\n" +
                "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Subject: Test utf-8 charset\r\n" +
                "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                "\r\n" +
                "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));
        clientConnection.write(ByteBuffer.wrap(String.format("a2 UID SEARCH THREADID %s\r\n", appendResult.getThreadId().serialize()).getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains(("* SEARCH " + appendResult.getId().getUid().asLong())));
    }

    // Not an MPT test as ThreadId is a variable server-set and implementation specific
    @Test
    void imapSearchShouldSupportEmailId() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        MessageManager.AppendResult appendResult = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Reply-To: b@linagora.com\r\n" +
                "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Subject: Test utf-8 charset\r\n" +
                "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                "\r\n" +
                "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));
        clientConnection.write(ByteBuffer.wrap(String.format("a2 UID SEARCH EMAILID %s\r\n", appendResult.getId().getMessageId().serialize()).getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains(("* SEARCH " + appendResult.getId().getUid().asLong())));
    }

    @Test
    void shouldConsiderCumulativeSizeForLiterals() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));


        String literal = "a".repeat(32 * 1024); // 32 KB
        clientConnection.write(ByteBuffer.wrap(("a2 SEARCH CHARSET UTF-8 TO {" + literal.length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> {
                for (int i = 0; i < 7000; i++) {
                    clientConnection.write(ByteBuffer.wrap((literal + " TO {" + literal.length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));
                }
                clientConnection.write(ByteBuffer.wrap((literal + " ALL\r\n").getBytes(StandardCharsets.UTF_8)));
            }).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectTooManyLiterals() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));


        String literal = "a";
        clientConnection.write(ByteBuffer.wrap(("a2 SEARCH CHARSET UTF-8 TO {" + literal.length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));

        try {
            for (int i = 0; i < 7000; i++) {
                clientConnection.write(ByteBuffer.wrap((literal + " TO {" + literal.length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));
            }
            clientConnection.write(ByteBuffer.wrap((literal + " ALL\r\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            // ignore
        }
        readStringUntil(clientConnection, s -> s.contains(("a2 BAD ")));
    }

    @Test
    void shouldRejectLongLineAfterLiteral() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String litteral = "a".repeat(8);
        clientConnection.write(ByteBuffer.wrap(("a2 SEARCH CHARSET UTF-8 TO {" + litteral.length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));
        String longLine = " ALL".repeat(1024 * 1024);
        clientConnection.write(ByteBuffer.wrap((litteral + longLine + "\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains(("a2 BAD ")));
    }

    @Test
    void passing2literalOnDifferentNetworkPackage() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String literal = "a".repeat(20);
        clientConnection.write(ByteBuffer.wrap(("a2 SEARCH CHARSET UTF-8 TO {" + literal.length() + "+}\r\n").getBytes()));
        clientConnection.write(ByteBuffer.wrap((literal + " TO {" + literal.length() + "+}\r\n").getBytes()));
        clientConnection.write(ByteBuffer.wrap((literal + " ALL\r\n").getBytes()));

        readStringUntil(clientConnection, s -> s.contains(("a2 OK ")));
    }

    @Test
    void passing2literalOnSameNetworkPackage() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String literal = "a".repeat(16);
        String s1 = "a2 SEARCH CHARSET UTF-8 TO {" + literal.length() + "+}\r\n" +
            literal + " TO {" + literal.length() + "+}\r\n" + literal + " ALL\r\n";
        clientConnection.write(ByteBuffer.wrap(s1.getBytes()));

        readStringUntil(clientConnection, s -> s.contains(("a2 OK ")));
    }

    @Test
    void passing2literalOnSameNetworkPackageWhenMoreThan16Chars() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String literal = "a".repeat(17);
        String s1 = "a2 SEARCH CHARSET UTF-8 TO {" + literal.length() + "+}\r\n" +
            literal + " TO {" + literal.length() + "+}\r\n" + literal + " ALL\r\n";
        clientConnection.write(ByteBuffer.wrap(s1.getBytes()));

        readStringUntil(clientConnection, s -> s.contains(("a2 OK ")));
    }

    @Disabled("JAMES-4043 Multiple literals and file literals are buggy")
    @Test
    void shouldAcceptSeveralFileLiteral() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String litteral = "a".repeat(72 * 1024);
        clientConnection.write(ByteBuffer.wrap(("a2 SEARCH CHARSET UTF-8 TO {" + litteral.length() + "+}\r\n").getBytes()));
        clientConnection.write(ByteBuffer.wrap((litteral + " TO {2+}\r\n").getBytes()));
        clientConnection.write(ByteBuffer.wrap(("aa ALL\r\n").getBytes()));

        readStringUntil(clientConnection, s -> s.contains(("a2 OK ")));
    }

    @Test
    void shouldRejectLongLineAfterLiteralWhenLogin() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a0 LOGIN {" + USER.asString().length() + "+}\r\n").getBytes(StandardCharsets.UTF_8)));
        clientConnection.write(ByteBuffer.wrap((USER.asString() + " " + "0123456789".repeat(1024 * 1024)).getBytes()));
        readStringUntil(clientConnection, s -> s.contains("a0 BAD"));
    }

    @Test
    void shouldRejectLongLiteralsWhenUnauthenticated() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a0 LOGIN " + USER.asString() + " {" + (10 * 1024) + "+}\r\n").getBytes(StandardCharsets.UTF_8)));
        clientConnection.write(ByteBuffer.wrap(("0123456789".repeat(1024) + " \r\n").getBytes()));

        readStringUntil(clientConnection, s -> s.contains(("a0 BAD ")));
    }

    @Test
    void searchingShouldSupportMultipleUTF8Criteria() throws Exception {
        String host = "127.0.0.1";
        Properties props = new Properties();
        props.put("mail.debug", "true");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imap");
        store.connect(host, port, USER.asString(), USER_PASS);
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_ONLY);

        SearchTerm subjectTerm = new SubjectTerm("java培训");
        SearchTerm fromTerm = new FromStringTerm("采购");
        SearchTerm recipientTerm = new RecipientStringTerm(Message.RecipientType.TO, "张三");
        SearchTerm ccRecipientTerm = new RecipientStringTerm(Message.RecipientType.CC, "李四");
        SearchTerm bccRecipientTerm = new RecipientStringTerm(Message.RecipientType.BCC, "王五");
        SearchTerm bodyTerm = new BodyTerm("天天向上");
        SearchTerm[] searchTerms = new SearchTerm[6];
        searchTerms[0] = subjectTerm;
        searchTerms[1] = bodyTerm;
        searchTerms[2] = fromTerm;
        searchTerms[3] = recipientTerm;
        searchTerms[4] = ccRecipientTerm;
        searchTerms[5] = bccRecipientTerm;
        SearchTerm andTerm = new AndTerm(searchTerms);

        assertThatCode(() -> folder.search(andTerm)).doesNotThrowAnyException();

        folder.close(false);
        store.close();
    }

    @Test
    void searchingASingleUTF8CriterionShouldComplete() throws Exception {
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Reply-To: b@linagora.com\r\n" +
                "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                "Subject: Test utf-8 charset\r\n" +
                "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                "\r\n" +
                "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

        String host = "127.0.0.1";
        Properties props = new Properties();
        props.put("mail.debug", "true");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imap");
        store.connect(host, port, USER.asString(), USER_PASS);
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_ONLY);

        SearchTerm bodyTerm = new BodyTerm("天天向上");

        assertThat(folder.search(bodyTerm)).hasSize(1);

        folder.close(false);
        store.close();
    }
}
