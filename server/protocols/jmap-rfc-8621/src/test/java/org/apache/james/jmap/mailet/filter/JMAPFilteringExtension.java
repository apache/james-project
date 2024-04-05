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

package org.apache.james.jmap.mailet.filter;

import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_MAILBOX_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_ADDRESS;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class JMAPFilteringExtension implements BeforeEachCallback, ParameterResolver {
    private static final DomainList NO_DOMAIN_LIST = null;

    class JMAPFilteringTestSystem {

        private final JMAPFiltering jmapFiltering;
        private final FilteringManagement filteringManagement;
        private final InMemoryMailboxManager mailboxManager;
        private final MailboxId recipient1Mailbox;

        JMAPFilteringTestSystem(JMAPFiltering jmapFiltering, FilteringManagement filteringManagement,
                                InMemoryMailboxManager mailboxManager) {
            this.jmapFiltering = jmapFiltering;
            this.filteringManagement = filteringManagement;
            this.mailboxManager = mailboxManager;
            try {
                this.recipient1Mailbox = createMailbox(RECIPIENT_1_USERNAME, RECIPIENT_1_MAILBOX_1.value());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public JMAPFiltering getJmapFiltering() {
            return jmapFiltering;
        }

        public FilteringManagement getFilteringManagement() {
            return filteringManagement;
        }

        public InMemoryMailboxManager getMailboxManager() {
            return mailboxManager;
        }

        public MailboxId getRecipient1MailboxId() {
            return recipient1Mailbox;
        }

        public MailboxId createMailbox(Username username, String mailboxName) throws Exception {
            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            return mailboxManager
                .createMailbox(MailboxPath.forUser(username, mailboxName), mailboxSession)
                .orElseThrow(() -> new RuntimeException("Missing mailboxId when creating mailbox"));
        }

        public void defineRulesForRecipient1(Rule.Condition... conditions) {
            defineRulesForRecipient1(Arrays.asList(conditions));
        }

        public void defineRulesForRecipient1(List<Rule.Condition> conditions) {
            AtomicInteger counter = new AtomicInteger();
            ImmutableList<Rule> rules = conditions
                .stream()
                .map(condition -> Rule.builder()
                    .id(Rule.Id.of(String.valueOf(counter.incrementAndGet())))
                    .name(String.valueOf(counter.incrementAndGet()))
                    .conditionGroup(condition)
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(testSystem.getRecipient1MailboxId().serialize())))
                    .build())
                .collect(ImmutableList.toImmutableList());

            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME, rules, Optional.empty())).block();
        }

        public FakeMail asMail(MimeMessageBuilder mimeMessageBuilder) throws MessagingException {
            return FakeMail.builder()
                .name("name")
                .sender(USER_1_ADDRESS)
                .recipients(RECIPIENT_1)
                .mimeMessage(mimeMessageBuilder)
                .build();
        }
    }

    private JMAPFilteringTestSystem testSystem;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        FilteringManagement filteringManagement = new EventSourcingFilteringManagement(new InMemoryEventStore());
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        ActionApplier.Factory actionApplierFactory = ActionApplier.factory(mailboxManager, new InMemoryId.Factory());

        JMAPFiltering jmapFiltering = new JMAPFiltering(filteringManagement, usersRepository, actionApplierFactory);
        jmapFiltering.init(mock(MailetConfig.class));

        testSystem = new JMAPFilteringTestSystem(jmapFiltering, filteringManagement, mailboxManager);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == JMAPFilteringTestSystem.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return testSystem;
    }
}
