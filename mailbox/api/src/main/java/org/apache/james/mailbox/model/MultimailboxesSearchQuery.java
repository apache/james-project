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

package org.apache.james.mailbox.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.search.MailboxQuery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class MultimailboxesSearchQuery {

    public static Builder from(SearchQuery searchQuery) {
        return new Builder(searchQuery);
    }

    public interface Namespace {
        boolean keepAccessible(Mailbox mailbox);

        MailboxQuery associatedMailboxSearchQuery();
    }

    public static class PersonalNamespace implements Namespace {
        private final MailboxSession session;

        public PersonalNamespace(MailboxSession session) {
            this.session = session;
        }

        @Override
        public boolean keepAccessible(Mailbox mailbox) {
            return mailbox.generateAssociatedPath().belongsTo(session);
        }

        @Override
        public MailboxQuery associatedMailboxSearchQuery() {
            return MailboxQuery.privateMailboxesBuilder(session)
                .matchesAllMailboxNames()
                .build();
        }
    }

    public static class AccessibleNamespace implements Namespace {
        @Override
        public boolean keepAccessible(Mailbox mailbox) {
            return true;
        }

        @Override
        public MailboxQuery associatedMailboxSearchQuery() {
            return MailboxQuery.builder().matchesAllMailboxNames().build();
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(AccessibleNamespace.class);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AccessibleNamespace;
        }
    }
    
    public static class Builder {
        private final SearchQuery searchQuery;
        private ImmutableSet.Builder<MailboxId> mailboxIds;
        private ImmutableSet.Builder<MailboxId> notInMailboxIds;
        private Optional<Namespace> namespace;

        private Builder(SearchQuery searchQuery) {
            Preconditions.checkNotNull(searchQuery);
            this.searchQuery = searchQuery;
            this.mailboxIds = ImmutableSet.builder();
            this.notInMailboxIds = ImmutableSet.builder();
            this.namespace = Optional.empty();
        }

        public Builder inMailboxes(Collection<MailboxId> mailboxIds) {
            this.mailboxIds.addAll(mailboxIds);
            return this;
        }
        
        public Builder inMailboxes(MailboxId... mailboxIds) {
            return inMailboxes(Arrays.asList(mailboxIds));
        }
        
        public Builder notInMailboxes(Collection<MailboxId> mailboxIds) {
            this.notInMailboxIds.addAll(mailboxIds);
            return this;
        }

        public Builder inNamespace(Namespace namespace) {
            this.namespace = Optional.of(namespace);
            return this;
        }
        
        public Builder notInMailboxes(MailboxId... mailboxIds) {
            return notInMailboxes(Arrays.asList(mailboxIds));
        }

        public MultimailboxesSearchQuery build() {
            return new MultimailboxesSearchQuery(searchQuery,
                mailboxIds.build(),
                notInMailboxIds.build(),
                namespace.orElse(new AccessibleNamespace()));
        }

    }

    private final SearchQuery searchQuery;
    private final ImmutableSet<MailboxId> inMailboxes;
    private final ImmutableSet<MailboxId> notInMailboxes;
    private final Namespace namespace;

    @VisibleForTesting
    MultimailboxesSearchQuery(SearchQuery searchQuery, ImmutableSet<MailboxId> inMailboxes, ImmutableSet<MailboxId> notInMailboxes, Namespace namespace) {
        this.searchQuery = searchQuery;
        this.inMailboxes = inMailboxes;
        this.notInMailboxes = notInMailboxes;
        this.namespace = namespace;
    }

    public ImmutableSet<MailboxId> getInMailboxes() {
        return inMailboxes;
    }
    
    public ImmutableSet<MailboxId> getNotInMailboxes() {
        return notInMailboxes;
    }
    
    public SearchQuery getSearchQuery() {
        return searchQuery;
    }

    public Namespace getNamespace() {
        return namespace;
    }
}
