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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

class JPAJamesServerTest implements JamesServerConcreteContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<JPAJamesConfiguration>(tmpDir ->
        JPAJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> JPAJamesServerMain.createServer(configuration)
            .overrideWith(new TestJPAConfigurationModule()))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private static final ConditionFactory AWAIT = Awaitility.await()
        .atMost(ONE_MINUTE)
        .with()
        .pollInterval(FIVE_HUNDRED_MILLISECONDS);
    static final String DOMAIN = "james.local";
    private static final String USER = "toto@" + DOMAIN;
    private static final String PASSWORD = "123456";

    private TestIMAPClient testIMAPClient;
    private SMTPMessageSender smtpMessageSender;

    @BeforeEach
    void setUp() {
        this.testIMAPClient = new TestIMAPClient();
        this.smtpMessageSender = new SMTPMessageSender(DOMAIN);
    }
    
    @Test
    void jpaGuiceServerShouldUpdateQuota(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);
        jamesServer.getProbe(QuotaProbesImpl.class).setGlobalMaxStorage(QuotaSizeLimit.size(50 * 1024));

        // ~ 12 KB email
        int imapPort = jamesServer.getProbe(ImapGuiceProbe.class).getImapPort();
        smtpMessageSender.connect(JAMES_SERVER_HOST, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD)
            .sendMessageWithHeaders(USER, USER, "header: toto\\r\\n\\r\\n" + Strings.repeat("0123456789\n", 1024));
        AWAIT.until(() -> testIMAPClient.connect(JAMES_SERVER_HOST, imapPort)
            .login(USER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .hasAMessage());

        assertThat(
            testIMAPClient.connect(JAMES_SERVER_HOST, imapPort)
                .login(USER, PASSWORD)
                .getQuotaRoot(TestIMAPClient.INBOX))
            .startsWith("* QUOTAROOT \"INBOX\" #private&toto@james.local\r\n" +
                "* QUOTA #private&toto@james.local (STORAGE 12 50)\r\n")
            .endsWith("OK GETQUOTAROOT completed.\r\n");
    }
}
