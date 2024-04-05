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

package org.apache.james.jmap;

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.GuiceJamesServer;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.vacation.api.VacationPatch;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.fge.lambdas.Throwing;

public abstract class VacationRelayIntegrationTest {

    private static final String USER = "benwa";
    private static final String USER_WITH_DOMAIN = USER + '@' + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String REASON = "Message explaining my wonderful vacations";

    @ClassRule
    public static MockSmtpTestRule fakeSmtp = new MockSmtpTestRule();

    private GuiceJamesServer guiceJamesServer;
    private JmapGuiceProbe jmapGuiceProbe;

    protected abstract GuiceJamesServer getJmapServer() throws IOException;

    protected abstract InMemoryDNSService getInMemoryDns();

    @Before
    public void setUp() throws Exception {
        getInMemoryDns()
            .registerMxRecord("yopmail.com", fakeSmtp.getDockerMockSmtp().getIPAddress());

        guiceJamesServer = getJmapServer();
        guiceJamesServer.start();

        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USER_WITH_DOMAIN, PASSWORD);
        MailboxProbe mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_WITH_DOMAIN, DefaultMailboxes.SENT);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_WITH_DOMAIN, DefaultMailboxes.INBOX);

        jmapGuiceProbe = guiceJamesServer.getProbe(JmapGuiceProbe.class);
    }

    @After
    public void teardown() {
        fakeSmtp.getDockerMockSmtp().getConfigurationClient().clearMails();
        fakeSmtp.getDockerMockSmtp().getConfigurationClient().clearBehaviors();
        guiceJamesServer.stop();
    }

    @Category(BasicFeature.class)
    @Test
    public void forwardingAnEmailShouldWork() throws Exception {
        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER_WITH_DOMAIN), VacationPatch
            .builder()
            .isEnabled(true)
            .textBody(REASON)
            .build());

        String externalMail = "ray@yopmail.com";

        SMTPClient smtpClient = new SMTPClient();
        smtpClient.connect(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
        smtpClient.helo(DOMAIN);
        smtpClient.setSender(externalMail);
        smtpClient.rcpt("<" + USER_WITH_DOMAIN + ">");
        smtpClient.sendShortMessageData("Reply-To: <" + externalMail + ">\r\n\r\ncontent");

        calmlyAwait.atMost(1, TimeUnit.MINUTES)
            .untilAsserted(() -> {
                List<Mail> mails = fakeSmtp.getDockerMockSmtp().getConfigurationClient()
                    .listMails();

                assertThat(mails).hasSize(1);
                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(mails.get(0).getEnvelope().getFrom()).isEqualTo(MailAddress.nullSender());
                    softly.assertThat(mails.get(0).getEnvelope().getRecipients())
                        .containsOnly(Mail.Recipient.builder()
                            .address(new MailAddress(externalMail))
                            .build());
                    softly.assertThat(mails.get(0).getMessage()).contains(REASON);
                }));
            });
    }
}
