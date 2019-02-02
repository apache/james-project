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
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.IsOverQuota;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.restassured.specification.RequestSpecification;

public class IsOverQuotaMatcherTest {

    private static final String OTHER_DOMAIN = "other.com";
    private static final String FROM = "fromuser@" + OTHER_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String BOUNCE_SENDER = "bounce.sender@" + DEFAULT_DOMAIN;

    private static final String OVER_QUOTA_MESSAGE = "The recipient is over quota";
    private static final QuotaSize SMALL_SIZE = QuotaSize.size(1);
    private static final QuotaSize LARGE_SIZE = QuotaSize.size(10000);
    private static final QuotaCount SMALL_COUNT = QuotaCount.count(0);
    private static final QuotaCount LARGE_COUNT = QuotaCount.count(100);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    
    private TemporaryJamesServer jamesServer;
    private RequestSpecification webAdminApi;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(ProcessorConfiguration.transport()
                        .addMailet(MailetConfiguration.builder()
                                .matcher(IsOverQuota.class)
                                .mailet(Bounce.class)
                                .addProperty("sender", BOUNCE_SENDER)
                                .addProperty("message", OVER_QUOTA_MESSAGE)
                                .addProperty("passThrough", "false"))
                        .addMailetsFrom(CommonProcessors.transport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        webAdminApi = given()
            .spec(WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build());

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addDomain(OTHER_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_SENDER, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void aBounceMessageShouldBeSentToTheSenderWhenRecipientAsReachedHisSizeQuota() throws Exception {
        webAdminApi.given()
            .body(SMALL_SIZE.asLong())
            .put("/quota/users/" + RECIPIENT + "/size");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        IMAPMessageReader messageReader = imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String bounceMessage = messageReader.readFirstMessage();
        assertThat(bounceMessage).contains(OVER_QUOTA_MESSAGE);
    }

    @Test
    public void aBounceMessageShouldBeSentToTheSenderWhenRecipientAsReachedHisDomainSizeQuota() throws Exception {
        webAdminApi.given()
            .body(SMALL_SIZE.asLong())
            .put("/quota/domains/" + DEFAULT_DOMAIN + "/size");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        IMAPMessageReader messageReader = imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String bounceMessage = messageReader.readFirstMessage();
        assertThat(bounceMessage).contains(OVER_QUOTA_MESSAGE);
    }

    @Test
    public void aBounceMessageShouldBeSentToTheRecipientWhenRecipientSizeQuotaIsNotExceeded() throws Exception {
        webAdminApi.given()
            .body(LARGE_SIZE.asLong())
            .put("/quota/users/" + RECIPIENT + "/size");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void aBounceMessageShouldBeSentToTheSenderWhenRecipientAsReachedHisCountQuota() throws Exception {
        webAdminApi.given()
            .body(SMALL_COUNT.asLong())
            .put("/quota/users/" + RECIPIENT + "/count");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        IMAPMessageReader messageReader = imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String bounceMessage = messageReader.readFirstMessage();
        assertThat(bounceMessage).contains(OVER_QUOTA_MESSAGE);
    }

    @Test
    public void aBounceMessageShouldBeSentToTheSenderWhenRecipientAsReachedHisDomainCountQuota() throws Exception {
        webAdminApi.given()
            .body(SMALL_COUNT.asLong())
            .put("/quota/domains/" + DEFAULT_DOMAIN + "/count");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        IMAPMessageReader messageReader = imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String bounceMessage = messageReader.readFirstMessage();
        assertThat(bounceMessage).contains(OVER_QUOTA_MESSAGE);
    }

    @Test
    public void aBounceMessageShouldBeSentToTheRecipientWhenRecipientCountQuotaIsNotExceeded() throws Exception {
        webAdminApi.given()
            .body(LARGE_COUNT.asLong())
            .put("/quota/users/" + RECIPIENT + "/count");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }
}
