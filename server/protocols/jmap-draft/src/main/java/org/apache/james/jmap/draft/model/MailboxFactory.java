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
package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxNamespace;
import org.apache.james.jmap.draft.model.mailbox.SortOrder;
import org.apache.james.jmap.draft.utils.quotas.DefaultQuotaLoader;
import org.apache.james.jmap.draft.utils.quotas.QuotaLoader;
import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.primitives.Booleans;

import reactor.core.publisher.Mono;

public class MailboxFactory {
    private static class MailboxTuple {
        public static MailboxTuple from(MailboxMetaData metaData) {
            return new MailboxTuple(metaData.getPath(), metaData.getCounters().sanitize(), metaData.getResolvedAcls());
        }

        public static Mono<MailboxTuple> from(MessageManager messageManager, MailboxSession session) {
            return Mono.from(messageManager.getMailboxCountersReactive(session))
                .map(MailboxCounters::sanitize)
                .map(Throwing.function(counters -> new MailboxTuple(messageManager.getMailboxPath(), counters, messageManager.getResolvedAcl(session))));
        }

        private final MailboxPath mailboxPath;
        private final MailboxCounters.Sanitized mailboxCounters;
        private final MailboxACL acl;

        private MailboxTuple(MailboxPath mailboxPath, MailboxCounters.Sanitized mailboxCounters, MailboxACL acl) {
            this.mailboxPath = mailboxPath;
            this.mailboxCounters = mailboxCounters;
            this.acl = acl;
        }
    }

    private final MailboxManager mailboxManager;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    public static class MailboxBuilder {
        private final MailboxFactory mailboxFactory;
        private QuotaLoader quotaLoader;
        private MailboxSession session;
        private Optional<MailboxId> id = Optional.empty();
        private Optional<MailboxMetaData> mailboxMetaData = Optional.empty();
        private Optional<Map<MailboxPath, MailboxMetaData>> userMailboxesMetadata = Optional.empty();

        private MailboxBuilder(MailboxFactory mailboxFactory, QuotaLoader quotaLoader) {
            this.mailboxFactory = mailboxFactory;
            this.quotaLoader = quotaLoader;
        }

        public MailboxBuilder id(MailboxId id) {
            this.id = Optional.of(id);
            return this;
        }

        public MailboxBuilder mailboxMetadata(MailboxMetaData mailboxMetaData) {
            this.mailboxMetaData = Optional.of(mailboxMetaData);
            return this;
        }

        public MailboxBuilder session(MailboxSession session) {
            this.session = session;
            return this;
        }

        public MailboxBuilder quotaLoader(QuotaLoader quotaLoader) {
            this.quotaLoader = quotaLoader;
            return this;
        }

        public MailboxBuilder usingPreloadedMailboxesMetadata(Optional<Map<MailboxPath, MailboxMetaData>> userMailboxesMetadata) {
            this.userMailboxesMetadata = userMailboxesMetadata;
            return this;
        }

        public Mono<Mailbox> build() {
            Preconditions.checkNotNull(session);

            MailboxId mailboxId = computeMailboxId();

            Mono<MailboxTuple> mailbox = mailboxMetaData.map(MailboxTuple::from)
                .map(Mono::just)
                .orElse(Mono.from(mailboxFactory.mailboxManager.getMailboxReactive(mailboxId, session))
                    .flatMap(messageManager -> MailboxTuple.from(messageManager, session)));

            return mailbox.flatMap(tuple -> mailboxFactory.from(
                mailboxId,
                tuple.mailboxPath,
                tuple.mailboxCounters,
                tuple.acl,
                userMailboxesMetadata,
                quotaLoader,
                session))
                .onErrorResume(MailboxNotFoundException.class, e -> Mono.empty());
        }

        private MailboxId computeMailboxId() {
            int idCount = Booleans.countTrue(id.isPresent(), mailboxMetaData.isPresent());
            Preconditions.checkState(idCount == 1, "You need exactly one 'id' 'mailboxMetaData'");
            return id.or(
                () -> mailboxMetaData.map(MailboxMetaData::getId))
                .get();
        }

        private Mono<MessageManager> mailbox(MailboxId mailboxId) {
            return Mono.from(mailboxFactory.mailboxManager.getMailboxReactive(mailboxId, session));
        }

        private Mono<MessageManager> retrieveCachedMailbox(MailboxId mailboxId, Mono<MessageManager> mailbox) throws MailboxNotFoundException {
            return mailbox
                .onErrorResume(MailboxNotFoundException.class, any -> Mono.empty())
                .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)));
        }
    }

    @Inject
    public MailboxFactory(MailboxManager mailboxManager, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) {
        this.mailboxManager = mailboxManager;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    public MailboxBuilder builder() {
        QuotaLoader defaultQuotaLoader = new DefaultQuotaLoader(quotaRootResolver, quotaManager);
        return new MailboxBuilder(this, defaultQuotaLoader);
    }

    private Mono<Mailbox> from(MailboxId mailboxId,
                         MailboxPath mailboxPath,
                         MailboxCounters.Sanitized mailboxCounters,
                         MailboxACL resolvedAcl,
                         Optional<Map<MailboxPath, MailboxMetaData>> userMailboxesMetadata,
                         QuotaLoader quotaLoader,
                         MailboxSession mailboxSession) {
        boolean isOwner = mailboxPath.belongsTo(mailboxSession);
        Optional<Role> role = Role.from(mailboxPath.getName())
            .filter(any -> mailboxPath.belongsTo(mailboxSession));

        Rights rights = Rights.fromACL(resolvedAcl)
            .removeEntriesFor(mailboxPath.getUser());
        Username username = mailboxSession.getUser();

        return Mono.zip(
                quotaLoader.getQuotas(mailboxPath),
                getParentIdFromMailboxPath(mailboxPath, userMailboxesMetadata, mailboxSession))
            .map(tuple -> Mailbox.builder()
                .id(mailboxId)
                .name(getName(mailboxPath, mailboxSession))
                .parentId(tuple.getT2().orElse(null))
                .role(role)
                .unreadMessages(mailboxCounters.getUnseen())
                .totalMessages(mailboxCounters.getCount())
                .sortOrder(SortOrder.getSortOrder(role))
                .sharedWith(rights)
                .mayAddItems(rights.mayAddItems(username).orElse(isOwner))
                .mayCreateChild(rights.mayCreateChild(username).orElse(isOwner))
                .mayDelete(rights.mayDelete(username).orElse(isOwner))
                .mayReadItems(rights.mayReadItems(username).orElse(isOwner))
                .mayRemoveItems(rights.mayRemoveItems(username).orElse(isOwner))
                .mayRename(rights.mayRename(username).orElse(isOwner))
                .namespace(getNamespace(mailboxPath, isOwner))
                .quotas(tuple.getT1())
                .build());
    }

    private MailboxNamespace getNamespace(MailboxPath mailboxPath, boolean isOwner) {
        if (isOwner) {
            return MailboxNamespace.personal();
        }
        return MailboxNamespace.delegated(mailboxPath.getUser());
    }

    @VisibleForTesting
    String getName(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        String name = mailboxPath.getName();
        if (name.contains(String.valueOf(mailboxSession.getPathDelimiter()))) {
            List<String> levels = Splitter.on(mailboxSession.getPathDelimiter()).splitToList(name);
            return levels.get(levels.size() - 1);
        }
        return name;
    }

    @VisibleForTesting
    Mono<Optional<MailboxId>> getParentIdFromMailboxPath(MailboxPath mailboxPath, Optional<Map<MailboxPath, MailboxMetaData>> userMailboxesMetadata,
                                                   MailboxSession mailboxSession) {
        List<MailboxPath> levels = mailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter());
        if (levels.size() <= 1) {
            return Mono.just(Optional.empty());
        }
        MailboxPath parent = levels.get(levels.size() - 2);
        return userMailboxesMetadata.map(list -> Mono.just(retrieveParentFromMetadata(parent, list)))
            .orElseGet(Throwing.supplier(() -> retrieveParentFromBackend(mailboxSession, parent)).sneakyThrow());
    }

    private Mono<Optional<MailboxId>> retrieveParentFromBackend(MailboxSession mailboxSession, MailboxPath parent) {
        return Mono.from(mailboxManager.getMailboxReactive(parent, mailboxSession))
            .map(MessageManager::getId)
            .map(Optional::of);
    }

    private Optional<MailboxId> retrieveParentFromMetadata(MailboxPath parent, Map<MailboxPath, MailboxMetaData> userMailboxesMetadata) {
        return Optional.ofNullable(userMailboxesMetadata.get(parent)).map(MailboxMetaData::getId);
    }
}