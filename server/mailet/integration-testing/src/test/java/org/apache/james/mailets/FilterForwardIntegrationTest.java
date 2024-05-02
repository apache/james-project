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

import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.FROM;
import static org.apache.james.mailets.configuration.CommonProcessors.RRT_ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

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
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FilteringManagementProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.inject.multibindings.Multibinder;

public class FilterForwardIntegrationTest {

    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    public static final Rule.ConditionGroup CONDITION_GROUP = Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, ALICE.asString()));
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);
    private static final MailRepositoryUrl CUSTOM_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/custom/");

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

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withOverrides(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(FilteringManagementProbeImpl.class))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(ProcessorConfiguration.root()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(JMAPFiltering.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", CUSTOM_REPOSITORY.asString())))
                .putProcessor(CommonProcessors.error())
                .putProcessor(CommonProcessors.rrtError())
                .putProcessor(CommonProcessors.transport())
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);

        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);

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
    void forwardShouldWork() throws Exception {
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).keepACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            Mail mail1 = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY, BOB.asMailAddress()).get(0);
            softly.assertThat(mail1.getRecipients()).containsOnly(BOB.asMailAddress());
            softly.assertThat(mail1.getMaybeSender().asOptional()).contains(ALICE.asMailAddress());

            Mail mail2 = mailRepositoryProbe.listMails(CUSTOM_REPOSITORY, CEDRIC.asMailAddress()).get(0);
            softly.assertThat(mail2.getRecipients()).containsOnly(CEDRIC.asMailAddress());
            softly.assertThat(mail2.getMaybeSender().asOptional()).contains(BOB.asMailAddress());
        }));
    }

    @Test
    void forwardShouldNotKeepACopyWhenKeepACopyIsFalse() throws Exception {
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).withoutACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> softly.assertThat(mailRepositoryProbe.listMails(CUSTOM_REPOSITORY)
            .anyMatch(Throwing.predicate(mail -> mail.getRecipients().contains(BOB.asMailAddress())))).isFalse()));
    }

    @Test
    void regularForwardShouldNotLeadToRecordingRRTError() throws Exception {
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).withoutACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        assertThat(mailRepositoryProbe.getRepositoryMailCount(RRT_ERROR_REPOSITORY)).isZero();
    }

    @Test
    void localCopyShouldNotLeadToRecordingRRTError() throws Exception {
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to(CEDRIC.asMailAddress()).keepACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 2L);

        assertThat(mailRepositoryProbe.getRepositoryMailCount(RRT_ERROR_REPOSITORY)).isZero();
    }

    @Test
    void localCopyAloneShouldNotLeadToRecordingRRTError() throws Exception {
        filteringManagementProbe.defineRulesForUser(BOB, asRule(Forward.to().keepACopy()));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        Awaitility.await().until(() -> mailRepositoryProbe.getRepositoryMailCount(CUSTOM_REPOSITORY) == 1L);

        assertThat(mailRepositoryProbe.getRepositoryMailCount(RRT_ERROR_REPOSITORY)).isZero();
    }
}
