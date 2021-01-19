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

import static org.apache.james.mailets.configuration.Constants.ALIAS;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SenderIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

public class SenderIsLocalIntegrationTest {
    private static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    private static final MailRepositoryUrl LOCAL_SENDER_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/local/sender/");
    private static final MailRepositoryUrl REMOTE_SENDER_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/remote/sender/");

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl probe;
    private RequestSpecification webAdminApi;

    @BeforeEach
    public void setUp(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(POSTMASTER)
                .putProcessor(transport()))
            .build(temporaryFolder);
        jamesServer.start();
        probe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @AfterEach
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void shouldMatchLocalSender() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(RECIPIENT, RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(LOCAL_SENDER_REPOSITORY) == 1);
    }

    @Test
    public void shouldMatchLocalSenderAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + RECIPIENT + "/sources/" + ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(ALIAS, RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(LOCAL_SENDER_REPOSITORY) == 1);
    }

    @Test
    public void shouldNotMatchRemoteSender() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("sender@domain.com", RECIPIENT);

        awaitAtMostOneMinute.until(() -> probe.getRepositoryMailCount(REMOTE_SENDER_REPOSITORY) == 1);
    }

    private ProcessorConfiguration.Builder transport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.builder()
                .matcher(SenderIsLocal.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", LOCAL_SENDER_REPOSITORY.asString()))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToRepository.class)
                .addProperty("repositoryPath", REMOTE_SENDER_REPOSITORY.asString()))
            .addMailetsFrom(CommonProcessors.transport());
    }
}
