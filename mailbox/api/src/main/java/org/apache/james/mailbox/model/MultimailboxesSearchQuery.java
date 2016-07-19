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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class MultimailboxesSearchQuery {

    public static Builder from(SearchQuery searchQuery) {
        return new Builder(searchQuery);
    }
    
    public static class Builder {
        
        private final SearchQuery searchQuery;
        private ImmutableSet.Builder<MailboxId> mailboxIds;

        private Builder(SearchQuery searchQuery) {
            Preconditions.checkNotNull(searchQuery);
            this.searchQuery = searchQuery;
            this.mailboxIds = ImmutableSet.builder();
        }
        
        public Builder inMailboxes(MailboxId... mailboxIds) {
            this.mailboxIds.addAll(Arrays.asList(mailboxIds));
            return this;
        }
        
        public MultimailboxesSearchQuery build() {
            return new MultimailboxesSearchQuery(searchQuery, mailboxIds.build());
        }
        
    }

    private final SearchQuery searchQuery;
    private final ImmutableSet<MailboxId> mailboxIds;

    @VisibleForTesting
    MultimailboxesSearchQuery(SearchQuery searchQuery, ImmutableSet<MailboxId> mailboxIds) {
        this.searchQuery = searchQuery;
        this.mailboxIds = mailboxIds;
    }

    public ImmutableSet<MailboxId> getMailboxIds() {
        return mailboxIds;
    }
    
    public SearchQuery getSearchQuery() {
        return searchQuery;
    }
}
