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

import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.FROM;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;

import java.io.File;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rule.Action;
import org.apache.james.jmap.api.filtering.Rule.Action.Forward;
import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FilteringManagementProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.Multibinder;

public class ForwardLoopIntegrationTest {

    private static final Username SENDER = Username.of("sender@" + DEFAULT_DOMAIN);
    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);
    private static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");
    public static final Rule.ConditionGroup CONDITION_GROUP = Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, NOT_CONTAINS, "AAA"));

    private static Rule.Builder asRule(Action.Forward forward) {
        return Rule.builder()
            .id(Rule.Id.of("1"))
            .name("rule 1")
            .conditionGroup(CONDITION_GROUP)
            .action(Action.builder().setForward(forward));
    }

    private TemporaryJamesServer jamesServer;
    private FilteringManagementProbeImpl filteringManagementProbe;
    private MailRepositoryProbeImpl mailRepositoryProbe;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    private DataProbeImpl dataProbe;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withOverrides(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(FilteringManagementProbeImpl.class))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RecipientRewriteTable.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(JMAPFiltering.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.transport())
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);

        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);

        dataProbe.addUser(SENDER.asString(), PASSWORD);
        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);

        mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);

        filteringManagementProbe = jamesServer.getProbe(FilteringManagementProbeImpl.class);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void filterForwardShouldNotCreateLoopError() throws Exception {
        filteringManagementProbe.defineRulesForUser(ALICE, asRule(Forward.to(BOB.asMailAddress()).withoutACopy()));
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).withoutACopy()));
        filteringManagementProbe.defineRulesForUser(CEDRIC, asRule(Forward.to(ALICE.asMailAddress()).withoutACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), ALICE.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            List<Mail> mails = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .collect(ImmutableList.toImmutableList());

            softly.assertThat(mails.get(0).getRecipients()).containsOnly(CEDRIC.asMailAddress());
        }));
    }

    @Test
    void filterForwardShouldNotCreateLoopErrorAndKeepEmailForUserWhoWantToKeepACopy() throws Exception {
        filteringManagementProbe.defineRulesForUser(ALICE, asRule(Forward.to(BOB.asMailAddress()).keepACopy()));
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).withoutACopy()));
        filteringManagementProbe.defineRulesForUser(CEDRIC, asRule(Forward.to(ALICE.asMailAddress()).withoutACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), ALICE.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            List<Mail> mailListOne = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY, ALICE.asMailAddress());
            softly.assertThat(mailListOne.get(0).getRecipients()).containsOnly(ALICE.asMailAddress());

            List<Mail> mailListTwo = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY, CEDRIC.asMailAddress());
            softly.assertThat(mailListTwo.get(0).getRecipients()).containsOnly(CEDRIC.asMailAddress());
        }));
    }

    @Test
    void regularForwardShouldNotCreateLoopError() throws Exception {
        dataProbe.addMapping(MappingSource.fromUser(ALICE), Mapping.forward(BOB.asString()));
        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(CEDRIC.asString()));
        dataProbe.addMapping(MappingSource.fromUser(CEDRIC), Mapping.forward(ALICE.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), ALICE.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            List<Mail> mails = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .collect(ImmutableList.toImmutableList());

            softly.assertThat(mails.get(0).getRecipients()).containsOnly(CEDRIC.asMailAddress());
        }));
    }

    @Test
    void regularForwardShouldNotCreateLoopErrorAndSendEmailToAppropriateReceivers() throws Exception {
        dataProbe.addMapping(MappingSource.fromUser(ALICE), Mapping.forward(BOB.asString()));
        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(CEDRIC.asString()));
        dataProbe.addMapping(MappingSource.fromUser(CEDRIC), Mapping.forward(ALICE.asString()));
        dataProbe.addMapping(MappingSource.fromUser(CEDRIC), Mapping.forward(SENDER.asString()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), ALICE.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            List<Mail> mails = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .collect(ImmutableList.toImmutableList());

            softly.assertThat(mails.get(0).getRecipients()).containsOnly(SENDER.asMailAddress());
        }));
    }

    @Test
    void forwardShouldNotCreateLoopErrorWhenFilterForwardAndRegularForwardWorkTogether() throws Exception {
        filteringManagementProbe.defineRulesForUser(ALICE, asRule(Forward.to(BOB.asMailAddress()).withoutACopy()));
        dataProbe.addMapping(MappingSource.fromUser(BOB), Mapping.forward(CEDRIC.asString()));
        filteringManagementProbe.defineRulesForUser(CEDRIC, asRule(Forward.to(ALICE.asMailAddress()).withoutACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER.asString(), PASSWORD)
            .sendMessage(SENDER.asString(), ALICE.asString());

        Awaitility.await().until(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());
        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            List<Mail> mails = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
                .collect(ImmutableList.toImmutableList());

            softly.assertThat(mails.get(0).getRecipients()).containsOnly(CEDRIC.asMailAddress());
        }));
    }
}
