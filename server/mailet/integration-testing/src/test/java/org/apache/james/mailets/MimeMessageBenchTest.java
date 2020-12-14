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
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.LogMessage;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Stopwatch;

/**
 * This benches the efficiency at which James mailetcontainer splits emails.
 */
@Disabled
public class MimeMessageBenchTest {
    private static String CONTENT = "0123456789\r\n".repeat(1024 * 10); // 120KB message

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration())
            .build(temporaryFolder.newFolder());
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser("rcpt1@" + DEFAULT_DOMAIN, PASSWORD);
        dataProbe.addUser("rcpt2@" + DEFAULT_DOMAIN, PASSWORD);
        dataProbe.addUser("rcpt3@" + DEFAULT_DOMAIN, PASSWORD);
        dataProbe.addUser("rcpt4@" + DEFAULT_DOMAIN, PASSWORD);
        dataProbe.addUser("rcpt5@" + DEFAULT_DOMAIN, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void receivedMessagesShouldContainDeliveredToHeaders() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        Stopwatch stopwatch = Stopwatch.createStarted();
        IntStream.range(0, 100)
            .forEach(Throwing.intConsumer(i -> messageSender.sendMessage(MailImpl.builder()
                .name("name" + i)
                .sender(FROM)
                .addRecipients("rcpt1@" + DEFAULT_DOMAIN,
                    "rcpt2@" + DEFAULT_DOMAIN,
                    "rcpt3@" + DEFAULT_DOMAIN,
                    "rcpt4@" + DEFAULT_DOMAIN,
                    "rcpt5@" + DEFAULT_DOMAIN)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("subject i")
                    .setText(CONTENT))
                .build())));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        System.out.println("Spent: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }


    private MailetContainer.Builder generateMailetContainerConfiguration() {
        return TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition("rcpt1@" + DEFAULT_DOMAIN)
                    .mailet(LogMessage.class)
                    .addProperty("passThrough", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition("rcpt2@" + DEFAULT_DOMAIN)
                    .mailet(LogMessage.class)
                    .addProperty("passThrough", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition("rcpt3@" + DEFAULT_DOMAIN)
                    .mailet(LogMessage.class)
                    .addProperty("passThrough", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition("rcpt4@" + DEFAULT_DOMAIN)
                    .mailet(LogMessage.class)
                    .addProperty("passThrough", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIs.class)
                    .matcherCondition("rcpt5@" + DEFAULT_DOMAIN)
                    .mailet(LogMessage.class)
                    .addProperty("passThrough", "true"))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));
    }
}
