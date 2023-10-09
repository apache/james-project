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

import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;

import java.io.File;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import com.github.fge.lambdas.Throwing;

public class ForwardIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM_LOCAL_PART = "fromUser";
    private static final String FROM = FROM_LOCAL_PART + "@" + DEFAULT_DOMAIN;
    private static final Username ALICE = Username.of("alice@" + JAMES_ANOTHER_DOMAIN);
    private static final Username BOB = Username.of("bob@" + JAMES_ANOTHER_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + JAMES_ANOTHER_DOMAIN);
    private static final Username DELPHINE = Username.of("delphine@" + JAMES_ANOTHER_DOMAIN);
    private static final Username GROUP = Username.of("group@" + DEFAULT_DOMAIN);
    private static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private MailRepositoryProbeImpl mailRepositoryProbe;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.deliverOnlyTransport())
                .putProcessor(CommonProcessors.error())
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RecipientRewriteTable.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())
                        .addProperty("rewriteSenderUponForward", "true"))))
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addDomain(JAMES_ANOTHER_DOMAIN);

        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);
        dataProbe.addUser(DELPHINE.asString(), PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);

        mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void forwardShouldRewriteTheSender() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(CEDRIC.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(CEDRIC.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void latestForwardShouldRewriteTheSender() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(CEDRIC.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(CEDRIC),
                Mapping.forward(DELPHINE.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(CEDRIC.asMailAddress());
        }));
    }

    @Test
    void groupShouldBeAppliedToForwardedMails() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(GROUP),
                Mapping.group(DELPHINE.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void forwardsShouldBeAppliedToGroupMembers() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(GROUP),
                Mapping.group(BOB.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, GROUP.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void forwardsShouldBeAppliedToAliases() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        String bobAlias = "bob-alias@" + JAMES_ANOTHER_DOMAIN;
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(Username.of(bobAlias)),
                Mapping.alias(BOB.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, bobAlias);

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void forwardsShouldApplyAliases() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        String delphineAlias = "delphine-alias@" + JAMES_ANOTHER_DOMAIN;
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(Username.of(delphineAlias)),
                Mapping.alias(DELPHINE.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            MailKey mailKey = mailRepositoryProbe.listMailKeys(CUSTOM_REPOSITORY).get(0);
            Mail mail = mailRepositoryProbe.getMail(CUSTOM_REPOSITORY, mailKey);

            softly.assertThat(mail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(mail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void forwardShouldSplitForwardedRecipient() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            Mail forwardedMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(DELPHINE.asMailAddress())))
                .findAny().get();
            Mail regularMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(ALICE.asMailAddress())))
                .findAny().get();
            softly.assertThat(forwardedMail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(forwardedMail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
            softly.assertThat(regularMail.getRecipients()).containsOnly(ALICE.asMailAddress());
            softly.assertThat(regularMail.getMaybeSender().asOptional()).contains(new MailAddress(FROM));
        }));
    }

    @Test
    void forwardShouldSplitForwardedRecipients() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(ALICE),
                Mapping.forward(CEDRIC.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            Mail forwardedMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(DELPHINE.asMailAddress())))
                .findAny().get();
            Mail regularMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(CEDRIC.asMailAddress())))
                .findAny().get();
            softly.assertThat(forwardedMail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(forwardedMail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
            softly.assertThat(regularMail.getRecipients()).containsOnly(CEDRIC.asMailAddress());
            softly.assertThat(regularMail.getMaybeSender().asOptional()).contains(ALICE.asMailAddress());
        }));
    }

    @Test
    void forwardShouldSplitLocalCopy() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(BOB.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            Mail forwardedMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(DELPHINE.asMailAddress())))
                .findAny().get();
            Mail regularMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .filter(Throwing.predicate(mail -> mail.getRecipients().contains(BOB.asMailAddress())))
                .findAny().get();
            softly.assertThat(forwardedMail.getRecipients()).containsOnly(DELPHINE.asMailAddress());
            softly.assertThat(forwardedMail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
            softly.assertThat(regularMail.getRecipients()).containsOnly(BOB.asMailAddress());
            softly.assertThat(regularMail.getMaybeSender().asOptional()).contains(new MailAddress(FROM));
        }));
    }

    @Test
    void forwardShouldSupportSeveralTargets() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(DELPHINE.asString()));
        jamesServer.getProbe(DataProbeImpl.class)
            .addMapping(MappingSource.fromUser(BOB),
                Mapping.forward(CEDRIC.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            Mail forwardedMail = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY).findAny().get();
            softly.assertThat(forwardedMail.getRecipients())
                .containsOnly(CEDRIC.asMailAddress(), DELPHINE.asMailAddress());
            softly.assertThat(forwardedMail.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }
}
