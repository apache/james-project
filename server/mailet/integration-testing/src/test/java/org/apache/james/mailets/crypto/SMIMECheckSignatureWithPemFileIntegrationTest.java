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

package org.apache.james.mailets.crypto;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.transport.mailets.SMIMECheckSignature;
import org.apache.james.transport.matcher.IsSMIMESigned;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

public class SMIMECheckSignatureWithPemFileIntegrationTest extends SMIMECheckSignatureIntegrationTest {
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    public void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .putProcessor(CommonProcessors.root())
            .putProcessor(CommonProcessors.error())
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                    .mailet(SMIMECheckSignature.class)
                    .matcher(IsSMIMESigned.class)
                    .addProperty("fileType", "pem")
                    .addProperty("pemFileName", FileSystem.CLASSPATH_PROTOCOL + "trusted_certificate.pem")
                    .addProperty("debug", "true"))
                .addMailet(MailetConfiguration.LOCAL_DELIVERY))
            .build();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(ZonedDateTimeProvider.class).toInstance(() -> DATE_2015))
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);
    }

    @AfterEach
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Override
    TestIMAPClient testIMAPClient() {
        return testIMAPClient;
    }

    @Override
    SMTPMessageSender messageSender() {
        return messageSender;
    }

    @Override
    TemporaryJamesServer jamesServer() {
        return jamesServer;
    }
}
