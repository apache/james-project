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
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FilteringManagementProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

public class FilterForwardIntegrationTest {

    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private FilteringManagementProbeImpl filteringManagementProbe;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .build(temporaryFolder);

        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);

        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);

        filteringManagementProbe = jamesServer.getProbe(FilteringManagementProbeImpl.class);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void forwardShouldWork() throws Exception {
        ImmutableList<MailAddress> forwardedMailAddresses = ImmutableList.of(CEDRIC.asMailAddress());
        Rule.Action.Forward forward = Rule.Action.Forward.of(forwardedMailAddresses, true);
        filteringManagementProbe.defineRulesForUser(BOB,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, ALICE.asString())))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of()),
                    false,
                    false,
                    false,
                    ImmutableList.of(),
                    Optional.of(forward)))
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
        assertThat(testIMAPClient.readFirstMessageHeaders()).contains("Return-Path: <" + BOB.asString() + ">");
    }

    @Test
    void forwardShouldKeepACopy() throws Exception {
        ImmutableList<MailAddress> forwardedMailAddresses = ImmutableList.of(CEDRIC.asMailAddress());
        Rule.Action.Forward forward = Rule.Action.Forward.of(forwardedMailAddresses, true);
        filteringManagementProbe.defineRulesForUser(BOB,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, ALICE.asString())))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of()),
                    false,
                    false,
                    false,
                    ImmutableList.of(),
                    Optional.of(forward)))
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void forwardShouldNotKeepACopyWhenKeepACopyIsFalse() throws Exception {
        ImmutableList<MailAddress> forwardedMailAddresses = ImmutableList.of(CEDRIC.asMailAddress());
        Rule.Action.Forward forward = Rule.Action.Forward.of(forwardedMailAddresses, false);
        filteringManagementProbe.defineRulesForUser(BOB,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, ALICE.asString())))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of()),
                    false,
                    false,
                    false,
                    ImmutableList.of(),
                    Optional.of(forward)))
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(ALICE.asString(), BOB.asString());

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
    }


}
