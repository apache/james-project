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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.mail.model.SerializableQuotaValue;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Strings;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class JPAJamesServerTest extends AbstractJamesServerTest {

    private static final ConditionFactory AWAIT = Awaitility.await()
        .atMost(Duration.ONE_MINUTE)
        .with()
        .pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS);
    private static final String DOMAIN = "james.local";
    private static final String USER = "toto@" + DOMAIN;
    private static final String PASSWORD = "123456";
    private static final String LOCALHOST = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final int IMAP_PORT = 1143;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DOMAIN);

    @Override
    protected GuiceJamesServer createJamesServer() throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return new GuiceJamesServer(configuration)
            .combineWith(JPAJamesServerMain.JPA_SERVER_MODULE, JPAJamesServerMain.PROTOCOLS)
            .overrideWith(new TestJPAConfigurationModule());
    }

    @Override
    protected void clean() {
    }

    @Test
    public void jpaGuiceServerShouldUpdateQuota() throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluentAddDomain(DOMAIN)
            .fluentAddUser(USER, PASSWORD);
        server.getProbe(QuotaProbesImpl.class).setGlobalMaxStorage(new SerializableQuotaValue<>(QuotaSize.size(50 * 1024)));

        // ~ 12 KB email
        smtpMessageSender.connect(LOCALHOST, SMTP_PORT)
            .sendMessageWithHeaders(USER, USER, "header: toto\\r\\n\\r\\n" + Strings.repeat("0123456789\n", 1024));
        AWAIT.until(() -> imapMessageReader.connect(LOCALHOST, IMAP_PORT)
            .login(USER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .hasAMessage());

        assertThat(
            imapMessageReader.connect(LOCALHOST, IMAP_PORT)
                .login(USER, PASSWORD)
                .getQuotaRoot(IMAPMessageReader.INBOX))
            .startsWith("* QUOTAROOT \"INBOX\" #private&toto@james.local\r\n" +
                "* QUOTA #private&toto@james.local (STORAGE 12 50)\r\n")
            .endsWith("OK GETQUOTAROOT completed.\r\n");
    }

}
