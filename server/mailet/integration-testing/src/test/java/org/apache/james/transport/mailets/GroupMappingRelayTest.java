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

import static io.restassured.RestAssured.given;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.hamcrest.Matchers.equalTo;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.IsSenderInRRTLoop;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.restassured.specification.RequestSpecification;

public class GroupMappingRelayTest {
    private static final String DOMAIN1 = "domain1.com";

    public static final String SENDER_LOCAL_PART = "fromuser";
    private static final String SENDER = SENDER_LOCAL_PART + "@" + DOMAIN1;
    private static final String GROUP_ON_DOMAIN1 = "group@" + DOMAIN1;

    private static final String MESSAGE_CONTENT = "any text";
    public static final String RRT_ERROR = "rrt-error";
    public static final MailRepositoryUrl RRT_ERROR_REPOSITORY = MailRepositoryUrl.from("file://var/mail/rrt-error/");

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private RequestSpecification webAdminApi;

    @ClassRule
    public static final FakeSmtp fakeSmtp = new FakeSmtp();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeClass
    public static void classSetUp() {
        fakeSmtp.awaitStarted(awaitAtMostOneMinute);
    }

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(RecipientRewriteTable.class)
                    .addProperty("errorProcessor", RRT_ERROR))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(VacationMailet.class))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport())
                .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                    .matcher(All.class)
                    .addProperty("gateway", fakeSmtp.getContainer().getContainerIp())))
            .putProcessor(ProcessorConfiguration.builder()
                .state(RRT_ERROR)
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("passThrough", "true")
                    .addProperty("repositoryPath", RRT_ERROR_REPOSITORY.asString()))
                .addMailet(MailetConfiguration.builder()
                    .matcher(IsSenderInRRTLoop.class)
                    .mailet(Null.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Bounce.class)));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN1);

        dataProbe.addUser(SENDER, PASSWORD);

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        webAdminApi = given()
            .spec(WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build());

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText(MESSAGE_CONTENT)
            .build();
    }

    @After
    public void tearDown() {
        fakeSmtp.clean();
        jamesServer.shutdown();
    }

    @Test
    public void sendMessageShouldSendAMessageToAnExternalGroupMember() throws Exception {
        String externalMail = "ray@yopmail.com";
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + externalMail);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        awaitAtMostOneMinute
            .pollDelay(Duration.ONE_HUNDRED_MILLISECONDS)
            .until(() -> fakeSmtp.isReceived(response -> response
                .body("[0].from", equalTo(SENDER))
                .body("[0].to[0]", equalTo(externalMail))
                .body("[0].text", equalTo(MESSAGE_CONTENT))));
    }
}
