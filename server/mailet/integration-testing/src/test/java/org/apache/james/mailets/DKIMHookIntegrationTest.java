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

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class DKIMHookIntegrationTest {
    private static final String FROM_LOCAL_PART = "fromUser";
    private static final String FROM = FROM_LOCAL_PART + "@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT_LOCAL_PART = "touser";
    private static final String RECIPIENT = RECIPIENT_LOCAL_PART + "@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;


    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void trustedAddressesShouldSkipDKIMHookCheck(@TempDir File temporaryFolder) throws Exception {
        initJamesServer(temporaryFolder);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessageWithHeaders(FROM, ImmutableList.of(RECIPIENT), "Return-Path: <btellier@linagora.com>\n" +
                "Content-Type: multipart/mixed; boundary=\"------------dsVZbfyUhMRjfuWnqQ80tHvc\"\n" +
                "Message-ID: <a7a376a1-cadb-45bc-9deb-39f749f62b6d@linagora.com>\n" +
                "Date: Tue, 7 Nov 2023 12:14:47 +0100\n" +
                "MIME-Version: 1.0\n" +
                "User-Agent: Mozilla Thunderbird\n" +
                "Content-Language: en-US\n" +
                "To: btellier@linagora.com\n" +
                "From: \"btellier@linagora.com\" <btellier@linagora.com>\n" +
                "Subject: Simple message\n" +
                "\n" +
                "This is a multi-part message in MIME format.\n" +
                "--------------dsVZbfyUhMRjfuWnqQ80tHvc\n" +
                "Content-Type: text/plain; charset=UTF-8; format=flowed\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                "\n" +
                "Simple body\n" +
                "\n" +
                "--------------dsVZbfyUhMRjfuWnqQ80tHvc\n" +
                "Content-Type: message/rfc822; name=BNPP ADVICE LOLO.eml\n" +
                "Content-Disposition: attachment; filename=\"BNPP.eml\"\n" +
                "\n" +
                "\n" +
                "--------------dsVZbfyUhMRjfuWnqQ80tHvc--");

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private void initJamesServer(File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer
            .builder()
            .withBase(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0")
                .addHook("org.apache.james.smtpserver.DKIMHook", ImmutableMap.of(
                    "forceCLRF", "true",
                    "signatureRequired", "true",
                    "onlyForSenderDomain", DEFAULT_DOMAIN,
                    "expectedDToken", DEFAULT_DOMAIN))
                .build())
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);
    }
}
