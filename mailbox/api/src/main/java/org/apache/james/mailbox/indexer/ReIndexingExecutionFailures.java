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

package org.apache.james.mailbox.indexer;

import java.util.List;
import java.util.Objects;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class ReIndexingExecutionFailures {
    public static class ReIndexingFailure {
        private final MailboxId mailboxId;
        private final MessageUid uid;

        public ReIndexingFailure(MailboxId mailboxId, MessageUid uid) {
            this.mailboxId = mailboxId;
            this.uid = uid;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public MessageUid getUid() {
            return uid;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ReIndexingFailure) {
                ReIndexingFailure that = (ReIndexingFailure) o;

                return Objects.equals(this.mailboxId, that.mailboxId)
                    && Objects.equals(this.uid, that.uid);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxId, uid);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("mailboxId", mailboxId)
                .add("uid", uid)
                .toString();
        }
    }

    private final List<ReIndexingFailure> messageFailures;
    private final List<MailboxId> mailboxFailures;

    public ReIndexingExecutionFailures(List<ReIndexingFailure> messageFailures, List<MailboxId> mailboxFailures) {
        this.messageFailures = messageFailures;
        this.mailboxFailures = mailboxFailures;
    }

    public List<ReIndexingFailure> messageFailures() {
        return ImmutableList.copyOf(messageFailures);
    }

    public List<MailboxId> mailboxFailures() {
        return ImmutableList.copyOf(mailboxFailures);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ReIndexingExecutionFailures) {
            ReIndexingExecutionFailures that = (ReIndexingExecutionFailures) o;

            return Objects.equals(this.messageFailures, that.messageFailures)
                && Objects.equals(this.mailboxFailures, that.mailboxFailures);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageFailures, mailboxFailures);
    }
}
