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

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;

import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailRepositoryIntegrationTest {
    private static final MailRepositoryUrl SENDER_DENIED_URL = MailRepositoryUrl.from("cassandra://var/mail/sender-denied/");
    private static final Duration ONE_MILLISECOND = new Duration(1, TimeUnit.MILLISECONDS);
    private static ConditionFactory await = Awaitility.with()
        .pollInterval(FIVE_HUNDRED_MILLISECONDS)
        .and()
        .with()
        .pollDelay(ONE_MILLISECOND)
        .await();
    private static final int LIMIT_TO_10_MESSAGES = 10;

    private SMTPMessageSender smtpMessageSender = new SMTPMessageSender("other.com");

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES)))
        .build();

    @Test
    void deniedSenderMailShouldBeStoredInCassandraMailRepositoryWhenConfigured(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain("domain.com")
            .addUser("user@domain.com", "secret");

        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("denied@other.com", "user@domain.com");

        MailRepositoryProbeImpl repositoryProbe = server.getProbe(MailRepositoryProbeImpl.class);
        await.until(() -> repositoryProbe.getRepositoryMailCount(SENDER_DENIED_URL) == 1);
    }
}
