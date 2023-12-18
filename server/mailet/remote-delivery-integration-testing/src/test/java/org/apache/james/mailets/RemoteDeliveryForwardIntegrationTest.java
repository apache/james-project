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

package org.apache.james.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension.DockerMockSmtp;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPMessageSenderExtension;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

class RemoteDeliveryForwardIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String REMOTE_RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    @RegisterExtension
    static MockSmtpServerExtension mockSmtpServerExtension = new MockSmtpServerExtension();
    @TempDir
    static File tempDir;
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    @RegisterExtension
    SMTPMessageSenderExtension smtpSenderExtension = new SMTPMessageSenderExtension(Domain.of(DEFAULT_DOMAIN));

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;
    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(DockerMockSmtp dockerMockSmtp) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(CommonProcessors.rrtErrorEnabledTransport()
                .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                    .matcher(All.class)
                    .addProperty("gateway", mockSmtpServerExtension.getMockSmtp().getIPAddress())))
            .putProcessor(CommonProcessors.rrtError());

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(tempDir);
        jamesServer.start();
        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    void forwardWithLocalCopyShouldSendTheMailToTheRemoteRecipient(SMTPMessageSender messageSender, DockerMockSmtp dockerMockSmtp) throws Exception {
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", RECIPIENT, REMOTE_RECIPIENT));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", RECIPIENT, RECIPIENT));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        Awaitility.await().untilAsserted(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());

        awaitAtMostOneMinute
            .until(() -> dockerMockSmtp.getConfigurationClient().listMails()
                .stream()
                .findFirst(), Optional::isPresent)
            .get();
    }

    @Test
    void forwardWithLocalCopyShouldStoreTheLocalCopy(SMTPMessageSender messageSender, DockerMockSmtp dockerMockSmtp) throws Exception {
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", RECIPIENT, REMOTE_RECIPIENT));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", RECIPIENT, RECIPIENT));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        Awaitility.await().untilAsserted(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());


        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }
}
