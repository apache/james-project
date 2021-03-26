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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.james.core.Username;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class IMAPServerTest {
    private static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    private static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    private static final Username USER = Username.of("user@domain.org");
    private static final String USER_PASS = "pass";
    public static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";
    private InMemoryIntegrationResources memoryIntegrationResources;

    private static XMLConfiguration getConfig(InputStream configStream) throws Exception {
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        XMLConfiguration xmlConfiguration = builder.getConfiguration();
        FileHandler fileHandler = new FileHandler(xmlConfiguration);
        fileHandler.load(configStream);
        configStream.close();

        return xmlConfiguration;
    }

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private IMAPServer createImapServer(String configurationFile) throws Exception {
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, USER_PASS);

        memoryIntegrationResources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        IMAPServer imapServer = new IMAPServer(
            DefaultImapDecoderFactory.createDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            DefaultImapProcessorFactory.createXListSupportingProcessor(
                memoryIntegrationResources.getMailboxManager(),
                memoryIntegrationResources.getEventBus(),
                new StoreSubscriptionManager(memoryIntegrationResources.getMailboxManager().getMapperFactory()),
                null,
                memoryIntegrationResources.getQuotaManager(),
                memoryIntegrationResources.getQuotaRootResolver(),
                metricFactory),
            new ImapMetrics(metricFactory));

        imapServer.configure(getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)));
        imapServer.init();

        return imapServer;
    }

    @Nested
    class NoLimit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerNoLimits.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void largeAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _129K_MESSAGE + ")\r\n");
        }
    }

    @Nested
    class Limit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void largeAppendsShouldBeRejected() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BAD APPEND failed.");
        }
    }

    @Nested
    class Search {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Disabled("JAMES-1489 IMAP Search do not support continuation")
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
            SearchTerm recipientTerm = new RecipientStringTerm(Message.RecipientType.TO,"张三");
            SearchTerm ccRecipientTerm = new RecipientStringTerm(Message.RecipientType.CC,"李四");
            SearchTerm bccRecipientTerm = new RecipientStringTerm(Message.RecipientType.BCC,"王五");
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
}
