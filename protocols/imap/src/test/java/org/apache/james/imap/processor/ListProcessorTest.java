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
package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.DefaultMailboxTyper;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.request.ListRequest.ListReturnOption;
import org.apache.james.imap.message.request.ListRequest.ListSelectOption;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

class ListProcessorTest {
    private static final Username BOB = Username.of("bob");

    private InMemoryMailboxManager mailboxManager;
    private SubscriptionManager subscriptionManager;
    private MailboxSession mailboxSession;
    private FakeImapSession session;
    private ListProcessor<ListRequest> testee;

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        subscriptionManager = mock(SubscriptionManager.class);
        testee = new ListProcessor<>(mailboxManager, new UnpooledStatusResponseFactory(), new RecordingMetricFactory(),
            subscriptionManager, null, new DefaultMailboxTyper(), PathConverter.Factory.DEFAULT);

        mailboxSession = mailboxManager.createSystemSession(BOB);
        session = new FakeImapSession();
        session.authenticated();
        session.setMailboxSession(mailboxSession);

        mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Drafts"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Sent"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Sent.2026"), mailboxSession);
    }

    @Test
    void listShouldReturnTheMailboxesMatchingAnyOfThePatterns() {
        assertThat(listedNames(request(ImmutableList.of("INBOX", "Sent.%"), EnumSet.noneOf(ListSelectOption.class))))
            .containsExactlyInAnyOrder("INBOX", "Sent.2026");
    }

    @Test
    void listShouldReturnOnceAMailboxMatchingSeveralPatterns() {
        assertThat(listedNames(request(ImmutableList.of("Sent", "S*", "*"), EnumSet.noneOf(ListSelectOption.class))))
            .containsExactlyInAnyOrder("INBOX", "Drafts", "Sent", "Sent.2026");
    }

    @Test
    void listShouldReturnOnceAMailboxMatchingPatternsOfDistinctNamespaces() {
        assertThat(listedNames(request(ImmutableList.of("Sent", "#private.*"), EnumSet.noneOf(ListSelectOption.class))))
            .containsExactlyInAnyOrder("Sent", "#private.INBOX", "#private.Drafts", "#private.Sent.2026");
    }

    @Test
    void listShouldReturnNothingWhenNoPatternMatches() {
        assertThat(listedNames(request(ImmutableList.of("Unknown", "Other%"), EnumSet.noneOf(ListSelectOption.class))))
            .isEmpty();
    }

    @Test
    void listSubscribedShouldReturnOnceASubscriptionMatchingSeveralPatterns() throws Exception {
        when(subscriptionManager.subscriptionsReactive(any())).thenReturn(Flux.just(
            MailboxPath.inbox(BOB), MailboxPath.forUser(BOB, "Sent"), MailboxPath.forUser(BOB, "Ghost")));

        assertThat(listedNames(request(ImmutableList.of("Sent", "*", "INBOX"), EnumSet.of(ListSelectOption.SUBSCRIBED))))
            .containsExactlyInAnyOrder("INBOX", "Sent", "Ghost");
    }

    @Test
    void listRecursiveMatchShouldReturnOnceAParentMatchingSeveralPatterns() throws Exception {
        when(subscriptionManager.subscriptionsReactive(any())).thenReturn(Flux.just(MailboxPath.forUser(BOB, "Sent.2026")));

        List<ListResponse> responses = listResponses(request(ImmutableList.of("Sent", "%"),
            EnumSet.of(ListSelectOption.SUBSCRIBED, ListSelectOption.RECURSIVEMATCH)));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getName()).isEqualTo("Sent");
        assertThat(responses.get(0).getChildInfos()).containsExactly(ListResponse.ChildInfo.SUBSCRIBED);
    }

    private ListRequest request(List<String> patterns, EnumSet<ListSelectOption> selectOptions) {
        return new ListRequest(ImapConstants.LIST_COMMAND, "", patterns, new Tag("A1"),
            selectOptions, EnumSet.noneOf(ListReturnOption.class), Optional.empty());
    }

    private List<String> listedNames(ListRequest request) {
        return listResponses(request).stream()
            .map(ListResponse::getName)
            .toList();
    }

    private List<ListResponse> listResponses(ListRequest request) {
        List<ImapResponseMessage> responses = new ArrayList<>();
        testee.processRequestReactive(request, session, new ImapProcessor.Responder() {
            @Override
            public void respond(ImapResponseMessage message) {
                responses.add(message);
            }

            @Override
            public void flush() {
            }
        }).block();
        return responses.stream()
            .filter(ListResponse.class::isInstance)
            .map(ListResponse.class::cast)
            .toList();
    }
}
