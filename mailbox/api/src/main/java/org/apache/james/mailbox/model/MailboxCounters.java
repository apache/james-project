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

import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MailboxCounters {

    public static class Builder {
        private Optional<Long> count = Optional.empty();
        private Optional<Long> unseen = Optional.empty();
        private Optional<MailboxId> mailboxId = Optional.empty();

        public Builder mailboxId(MailboxId mailboxId) {
            this.mailboxId = Optional.of(mailboxId);
            return this;
        }

        public Builder count(long count) {
            this.count = Optional.of(count);
            return this;
        }

        public Builder unseen(long unseen) {
            this.unseen = Optional.of(unseen);
            return this;
        }

        public MailboxCounters build() {
            Preconditions.checkState(count.isPresent(), "count is compulsory");
            Preconditions.checkState(unseen.isPresent(), "unseen is compulsory");
            Preconditions.checkState(mailboxId.isPresent(), "mailboxId is compulsory");
            return new MailboxCounters(mailboxId.get(), count.get(), unseen.get());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final MailboxId mailboxId;
    private final long count;
    private final long unseen;

    private MailboxCounters(MailboxId mailboxId, long count, long unseen) {
        this.mailboxId = mailboxId;
        this.count = count;
        this.unseen = unseen;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public long getCount() {
        return count;
    }

    public long getUnseen() {
        return unseen;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxCounters) {
            MailboxCounters that = (MailboxCounters) o;

            return Objects.equal(this.count, that.count)
                && Objects.equal(this.unseen, that.unseen)
                && Objects.equal(this.mailboxId, that.mailboxId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(count, unseen, mailboxId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxId", mailboxId)
            .add("count", count)
            .add("unseen", unseen)
            .toString();
    }
}
