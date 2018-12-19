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

package org.apache.james.mailbox.store.event;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageMoveEvent;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

public class EventFactory {
    public abstract static class MailboxEventBuilder<T extends MailboxEventBuilder> {
        protected MailboxPath path;
        protected MailboxId mailboxId;
        protected User user;
        protected MailboxSession.SessionId sessionId;

        protected abstract T backReference();

        public T mailbox(Mailbox mailbox) {
            path(mailbox.generateAssociatedPath());
            mailboxId(mailbox.getMailboxId());
            return backReference();
        }

        public T mailboxSession(MailboxSession mailboxSession) {
            user(mailboxSession.getUser());
            sessionId(mailboxSession.getSessionId());
            return backReference();
        }

        public T mailboxId(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
            return backReference();
        }

        public T path(MailboxPath path) {
            this.path = path;
            return backReference();
        }

        public T user(User user) {
            this.user = user;
            return backReference();
        }

        public T sessionId(MailboxSession.SessionId sessionId) {
            this.sessionId = sessionId;
            return backReference();
        }

        protected void mailboxEventChecks() {
            Preconditions.checkState(user != null, "Field `user` is compulsory");
            Preconditions.checkState(mailboxId != null, "Field `mailboxId` is compulsory");
            Preconditions.checkState(path != null, "Field `path` is compulsory");
            Preconditions.checkState(sessionId != null, "Field `sessionId` is compulsory");
        }
    }

    public abstract static class MessageMetaDataEventBuilder<T extends MessageMetaDataEventBuilder> extends MailboxEventBuilder<T> {
        protected final ImmutableList.Builder<MessageMetaData> metaData;

        protected MessageMetaDataEventBuilder() {
            metaData = ImmutableList.builder();
        }

        protected abstract T backReference();

        public T addMessage(MailboxMessage message) {
            this.addMetaData(message.metaData());
            return backReference();
        }

        public T addMessages(Iterable<MailboxMessage> messages) {
            this.addMetaData(ImmutableList.copyOf(messages)
                .stream()
                .map(MailboxMessage::metaData)
                .collect(Guavate.toImmutableList()));
            return backReference();
        }

        public T addMetaData(MessageMetaData metaData) {
            this.metaData.add(metaData);
            return backReference();
        }

        public T addMetaData(Iterable<MessageMetaData> metaData) {
            this.metaData.addAll(metaData);
            return backReference();
        }

        protected ImmutableSortedMap<MessageUid, MessageMetaData> metaDataAsMap() {
            return metaData.build()
                .stream()
                .collect(Guavate.toImmutableSortedMap(MessageMetaData::getUid));
        }
    }

    public static class AddedBuilder extends MessageMetaDataEventBuilder<AddedBuilder> {
        @Override
        protected AddedBuilder backReference() {
            return this;
        }

        public MailboxListener.Added build() {
            mailboxEventChecks();

            return new MailboxListener.Added(
                sessionId,
                user,
                path,
                mailboxId,
                metaDataAsMap());
        }
    }

    public static class ExpungedBuilder extends MessageMetaDataEventBuilder<ExpungedBuilder> {
        @Override
        protected ExpungedBuilder backReference() {
            return this;
        }

        public MailboxListener.Expunged build() {
            mailboxEventChecks();
            
            return new MailboxListener.Expunged(
                sessionId,
                user,
                path,
                mailboxId,
                metaDataAsMap());
        }
    }

    public static class MailboxAclUpdatedBuilder extends MailboxEventBuilder<MailboxAclUpdatedBuilder> {
        private ACLDiff aclDiff;

        public MailboxAclUpdatedBuilder aclDiff(ACLDiff aclDiff) {
            this.aclDiff = aclDiff;
            return this;
        }

        @Override
        protected MailboxAclUpdatedBuilder backReference() {
            return this;
        }

        public MailboxListener.MailboxACLUpdated build() {
            Preconditions.checkState(aclDiff != null, "Field `aclDiff` is compulsory");
            mailboxEventChecks();

            return new MailboxListener.MailboxACLUpdated(
                sessionId,
                user,
                path,
                aclDiff,
                mailboxId);
        }
    }

    public static class MailboxAddedBuilder extends MailboxEventBuilder<MailboxAddedBuilder> {
        @Override
        protected MailboxAddedBuilder backReference() {
            return this;
        }

        public MailboxListener.MailboxAdded build() {
            mailboxEventChecks();

            return new MailboxListener.MailboxAdded(sessionId, user, path, mailboxId);
        }
    }

    public static class MailboxDeletionBuilder extends MailboxEventBuilder<MailboxDeletionBuilder> {
        private QuotaRoot quotaRoot;
        private QuotaCount deletedMessageCount;
        private QuotaSize totalDeletedSize;

        @Override
        protected MailboxDeletionBuilder backReference() {
            return this;
        }

        public MailboxDeletionBuilder quotaRoot(QuotaRoot quotaRoot) {
            this.quotaRoot = quotaRoot;
            return this;
        }

        public MailboxDeletionBuilder deletedMessageCount(QuotaCount deletedMessageCount) {
            this.deletedMessageCount = deletedMessageCount;
            return this;
        }

        public MailboxDeletionBuilder totalDeletedSize(QuotaSize totalDeletedSize) {
            this.totalDeletedSize = totalDeletedSize;
            return this;
        }

        public MailboxListener.MailboxDeletion build() {
            mailboxEventChecks();
            Preconditions.checkState(quotaRoot != null, "Field `quotaRoot` is compulsory");
            Preconditions.checkState(deletedMessageCount != null, "Field `deletedMessageCount` is compulsory");
            Preconditions.checkState(totalDeletedSize != null, "Field `totalDeletedSize` is compulsory");

            return new MailboxListener.MailboxDeletion(sessionId, user, path, quotaRoot, deletedMessageCount, totalDeletedSize, mailboxId);
        }
    }

    public static class MailboxRenamedBuilder extends MailboxEventBuilder<MailboxRenamedBuilder> {
        private MailboxPath newPath;

        @Override
        protected MailboxRenamedBuilder backReference() {
            return this;
        }

        public MailboxRenamedBuilder newPath(MailboxPath newPath) {
            this.newPath = newPath;
            return this;
        }

        public MailboxRenamedBuilder oldPath(MailboxPath oldPath) {
            this.path = oldPath;
            return this;
        }

        public MailboxListener.MailboxRenamed build() {
            mailboxEventChecks();
            Preconditions.checkState(path != null, "Field `newPath` is compulsory");

            return new MailboxListener.MailboxRenamed(
                sessionId,
                user,
                path,
                mailboxId,
                newPath);
        }
    }

    public static class FlagsUpdatedBuilder extends MailboxEventBuilder<FlagsUpdatedBuilder> {
        private final ImmutableList.Builder<UpdatedFlags> updatedFlags;

        public FlagsUpdatedBuilder() {
            updatedFlags = ImmutableList.builder();
        }

        public FlagsUpdatedBuilder updatedFags(Iterable<UpdatedFlags> updatedFlags) {
            this.updatedFlags.addAll(updatedFlags);
            return this;
        }

        public FlagsUpdatedBuilder updatedFags(UpdatedFlags updatedFlags) {
            this.updatedFlags.add(updatedFlags);
            return this;
        }

        @Override
        protected FlagsUpdatedBuilder backReference() {
            return this;
        }

        public MailboxListener.FlagsUpdated build() {
            mailboxEventChecks();

            ImmutableList<UpdatedFlags> updatedFlags = this.updatedFlags.build();

            return new MailboxListener.FlagsUpdated(sessionId, user, path, mailboxId, updatedFlags);
        }
    }

    public AddedBuilder added() {
        return new AddedBuilder();
    }

    public ExpungedBuilder expunged() {
        return new ExpungedBuilder();
    }

    public FlagsUpdatedBuilder flagsUpdated() {
        return new FlagsUpdatedBuilder();
    }

    public MailboxRenamedBuilder mailboxRenamed() {
        return new MailboxRenamedBuilder();
    }

    public MailboxDeletionBuilder mailboxDeleted() {
        return new MailboxDeletionBuilder();
    }

    public MailboxAddedBuilder mailboxAdded() {
        return new MailboxAddedBuilder();
    }

    public MailboxAclUpdatedBuilder aclUpdated() {
        return new MailboxAclUpdatedBuilder();
    }

    public MessageMoveEvent.Builder moved() {
        return MessageMoveEvent.builder();
    }
}
