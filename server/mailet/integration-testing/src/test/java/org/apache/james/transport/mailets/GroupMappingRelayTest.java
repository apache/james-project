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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

class GroupMappingRelayTest {
    private static final String DOMAIN1 = "domain1.com";

    public static final String SENDER_LOCAL_PART = "fromuser";
    private static final String SENDER = SENDER_LOCAL_PART + "@" + DOMAIN1;
    private static final String GROUP_ON_DOMAIN1 = "group@" + DOMAIN1;

    private static final String MESSAGE_CONTENT = "any text";

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private RequestSpecification webAdminApi;

    @RegisterExtension
    public static final FakeSmtp fakeSmtp = FakeSmtp.withDefaultPort();
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(CommonProcessors.rrtErrorEnabledTransport()
                .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                    .matcher(All.class)
                    .addProperty("gateway", fakeSmtp.getContainer().getContainerIp())))
            .putProcessor(CommonProcessors.rrtError());

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN1);

        dataProbe.addUser(SENDER, PASSWORD);

        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText(MESSAGE_CONTENT)
            .build();
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void sendMessageShouldSendAMessageToAnExternalGroupMember() throws Exception {
        String externalMail = "ray@yopmail.com";
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + externalMail);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        awaitAtMostOneMinute
            .untilAsserted(() -> fakeSmtp.assertEmailReceived(response -> response
                .body("[0].from", equalTo(SENDER))
                .body("[0].to[0]", equalTo(externalMail))
                .body("[0].text", equalTo(MESSAGE_CONTENT))));
    }
}
