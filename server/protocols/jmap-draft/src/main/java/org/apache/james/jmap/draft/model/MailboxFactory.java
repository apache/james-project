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
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxNamespace;
import org.apache.james.jmap.draft.model.mailbox.Quotas;
import org.apache.james.jmap.draft.model.mailbox.Rights;
import org.apache.james.jmap.draft.model.mailbox.SortOrder;
import org.apache.james.jmap.draft.utils.quotas.DefaultQuotaLoader;
import org.apache.james.jmap.draft.utils.quotas.QuotaLoader;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxException;
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
    private final MailboxManager mailboxManager;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    public static class MailboxBuilder {
        private final MailboxFactory mailboxFactory;
        private QuotaLoader quotaLoader;
        private MailboxSession session;
        private Optional<MailboxId> id = Optional.empty();
        private Optional<MailboxMetaData> mailboxMetaData = Optional.empty();
        private Optional<List<MailboxMetaData>> userMailboxesMetadata = Optional.empty();

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

        public MailboxBuilder usingPreloadedMailboxesMetadata(Optional<List<MailboxMetaData>> userMailboxesMetadata) {
            this.userMailboxesMetadata = userMailboxesMetadata;
            return this;
        }

        public Optional<Mailbox> build() {
            Preconditions.checkNotNull(session);

            try {
                MailboxId mailboxId = computeMailboxId();
                Mono<MessageManager> mailbox = mailbox(mailboxId).cache();

                MailboxACL mailboxACL = mailboxMetaData.map(MailboxMetaData::getResolvedAcls)
                    .orElseGet(Throwing.supplier(() -> retrieveCachedMailbox(mailboxId, mailbox).getResolvedAcl(session)).sneakyThrow());

                MailboxPath mailboxPath = mailboxMetaData.map(MailboxMetaData::getPath)
                    .orElseGet(Throwing.supplier(() -> retrieveCachedMailbox(mailboxId, mailbox).getMailboxPath()).sneakyThrow());

                MailboxCounters.Sanitized mailboxCounters = mailboxMetaData.map(MailboxMetaData::getCounters)
                    .orElseGet(Throwing.supplier(() -> retrieveCachedMailbox(mailboxId, mailbox).getMailboxCounters(session)).sneakyThrow())
                    .sanitize();

                return Optional.of(mailboxFactory.from(
                    mailboxId,
                    mailboxPath,
                    mailboxCounters,
                    mailboxACL,
                    userMailboxesMetadata,
                    quotaLoader,
                    session));
            } catch (MailboxNotFoundException e) {
                return Optional.empty();
            } catch (MailboxException e) {
                throw new RuntimeException(e);
            }
        }

        private MailboxId computeMailboxId() {
            int idCount = Booleans.countTrue(id.isPresent(), mailboxMetaData.isPresent());
            Preconditions.checkState(idCount == 1, "You need exactly one 'id' 'mailboxMetaData'");
            return id.or(
                () -> mailboxMetaData.map(MailboxMetaData::getId))
                .get();
        }

        private Mono<MessageManager> mailbox(MailboxId mailboxId) {
            return Mono.fromCallable(() -> mailboxFactory.mailboxManager.getMailbox(mailboxId, session));
        }

        private MessageManager retrieveCachedMailbox(MailboxId mailboxId, Mono<MessageManager> mailbox) throws MailboxNotFoundException {
            return mailbox
                .onErrorResume(MailboxNotFoundException.class, any -> Mono.empty())
                .blockOptional()
                .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
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

    private Mailbox from(MailboxId mailboxId,
                         MailboxPath mailboxPath,
                         MailboxCounters.Sanitized mailboxCounters,
                         MailboxACL resolvedAcl,
                         Optional<List<MailboxMetaData>> userMailboxesMetadata,
                         QuotaLoader quotaLoader,
                         MailboxSession mailboxSession) throws MailboxException {
        boolean isOwner = mailboxPath.belongsTo(mailboxSession);
        Optional<Role> role = Role.from(mailboxPath.getName());

        Rights rights = Rights.fromACL(resolvedAcl)
            .removeEntriesFor(mailboxPath.getUser());
        Username username = mailboxSession.getUser();

        Quotas quotas = quotaLoader.getQuotas(mailboxPath);

        return Mailbox.builder()
            .id(mailboxId)
            .name(getName(mailboxPath, mailboxSession))
            .parentId(getParentIdFromMailboxPath(mailboxPath, userMailboxesMetadata, mailboxSession).orElse(null))
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
            .quotas(quotas)
            .build();
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
    Optional<MailboxId> getParentIdFromMailboxPath(MailboxPath mailboxPath, Optional<List<MailboxMetaData>> userMailboxesMetadata,
                                                   MailboxSession mailboxSession) throws MailboxException {
        List<MailboxPath> levels = mailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter());
        if (levels.size() <= 1) {
            return Optional.empty();
        }
        MailboxPath parent = levels.get(levels.size() - 2);
        return userMailboxesMetadata.map(list -> retrieveParentFromMetadata(parent, list))
            .orElseGet(Throwing.supplier(() -> retrieveParentFromBackend(mailboxSession, parent)).sneakyThrow());
    }

    private Optional<MailboxId> retrieveParentFromBackend(MailboxSession mailboxSession, MailboxPath parent) throws MailboxException {
        return Optional.of(
            mailboxManager.getMailbox(parent, mailboxSession)
                .getId());
    }

    private Optional<MailboxId> retrieveParentFromMetadata(MailboxPath parent, List<MailboxMetaData> list) {
        return list.stream()
            .filter(metadata -> metadata.getPath().equals(parent))
            .map(MailboxMetaData::getId)
            .findAny();
    }
}