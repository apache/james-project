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
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.GuiceJamesServer;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public abstract class VacationRelayIntegrationTest {

    private static final String USER = "benwa";
    private static final String USER_WITH_DOMAIN = USER + '@' + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String REASON = "Message explaining my wonderful vacations";

    @ClassRule
    public static FakeSmtp fakeSmtp = FakeSmtp.withDefaultPort();

    private GuiceJamesServer guiceJamesServer;
    private JmapGuiceProbe jmapGuiceProbe;

    protected abstract GuiceJamesServer getJmapServer() throws IOException;

    protected abstract InMemoryDNSService getInMemoryDns();

    @Before
    public void setUp() throws Exception {
        getInMemoryDns()
            .registerMxRecord("yopmail.com", fakeSmtp.getContainer().getContainerIp());

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
        fakeSmtp.clean();
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
            .untilAsserted(() ->
                fakeSmtp.assertEmailReceived(response -> response
                    .body("[0].from", equalTo(USER_WITH_DOMAIN))
                    .body("[0].to[0]", equalTo(externalMail))
                    .body("[0].text", equalTo(REASON))));
    }
}
