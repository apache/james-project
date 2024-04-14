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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.methods.Method;

import com.google.common.collect.ImmutableList;

public class GetMailboxesResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String accountId;
        private String state;
        private final ImmutableList.Builder<Mailbox> mailboxes;
        private final ImmutableList.Builder<String> notFoundBuilder;

        private Builder() {
            mailboxes = ImmutableList.builder();
            notFoundBuilder = ImmutableList.builder();
        }

        public Builder accountId(String accountId) {
            if (accountId != null) {
                throw new NotImplementedException("not implemented");
            }
            return this;
        }

        public Builder state(String state) {
            if (state != null) {
                throw new NotImplementedException("not implemented");
            }
            return this;
        }

        public Builder add(Mailbox mailbox) {
            this.mailboxes.add(mailbox);
            return this;
        }

        public Builder addAll(List<Mailbox> list) {
            this.mailboxes.addAll(list);
            return this;
        }
        
        public Builder notFound(String[] notFound) {
            if (notFound != null) {
                throw new NotImplementedException("not implemented");
            }
            return this;
        }

        public GetMailboxesResponse build() {
            ImmutableList<String> notFound = notFoundBuilder.build();
            return new GetMailboxesResponse(accountId, state, mailboxes.build(), 
                    notFound.isEmpty() ? Optional.empty() : Optional.of(notFound));
        }
    }

    private final String accountId;
    private final String state;
    private final List<Mailbox> list;
    private final Optional<List<String>> notFound;

    private GetMailboxesResponse(String accountId, String state, List<Mailbox> list, Optional<List<String>> notFound) {
        this.accountId = accountId;
        this.state = state;
        this.list = list;
        this.notFound = notFound;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getState() {
        return state;
    }

    public List<Mailbox> getList() {
        return list;
    }

    public Optional<List<String>> getNotFound() {
        return notFound;
    }
}
