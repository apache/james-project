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

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

public class EventFactory {
    @FunctionalInterface
    public interface  RequireEventId<T> {
        T eventId(Event.EventId eventId);

        default T randomEventId() {
            return eventId(Event.EventId.random());
        }
    }

    @FunctionalInterface
    public interface RequireUser<T> {
        T user(User user);
    }

    @FunctionalInterface
    public interface RequireSessionId<T> {
        T sessionId(MailboxSession.SessionId sessionId);
    }

    @FunctionalInterface
    public interface RequireSession<T> extends RequireUser<RequireSessionId<T>> {
        default T mailboxSession(MailboxSession session) {
            return user(session.getUser())
                .sessionId(session.getSessionId());
        }
    }

    @FunctionalInterface
    public interface RequireMailboxId<T> {
        T mailboxId(MailboxId mailboxId);
    }

    @FunctionalInterface
    public interface RequirePath<T> {
        T mailboxPath(MailboxPath path);
    }

    @FunctionalInterface
    public interface RequireMailbox<T> extends RequireMailboxId<RequirePath<T>> {
        default T mailbox(Mailbox mailbox) {
            return mailboxId(mailbox.getMailboxId())
                .mailboxPath(mailbox.generateAssociatedPath());
        }
    }

    @FunctionalInterface
    public interface RequireNewPath<T> {
        T newPath(MailboxPath path);
    }

    @FunctionalInterface
    public interface RequireOldPath<T> {
        T oldPath(MailboxPath path);
    }

    @FunctionalInterface
    public interface RequireMetadata<T> {
        T metaData(SortedMap<MessageUid, MessageMetaData> metaData);

        default T addMetaData(MessageMetaData metaData) {
            return metaData(ImmutableSortedMap.of(metaData.getUid(), metaData));
        }

        default T addMessage(MailboxMessage message) {
            return addMetaData(message.metaData());
        }

        default T addMetaData(Iterable<MessageMetaData> metaData) {
            return metaData(ImmutableList.copyOf(metaData)
                .stream()
                .collect(Guavate.toImmutableSortedMap(MessageMetaData::getUid)));
        }

        default T addMetaData(Iterator<MessageMetaData> metaData) {
            return addMetaData(ImmutableList.copyOf(metaData));
        }

        default T addMessages(Iterable<MailboxMessage> messages) {
            return metaData(ImmutableList.copyOf(messages)
                .stream()
                .map(MailboxMessage::metaData)
                .collect(Guavate.toImmutableSortedMap(MessageMetaData::getUid)));
        }
    }

    @FunctionalInterface
    public interface RequireAclDiff<T> {
        T aclDiff(ACLDiff aclDiff);
    }

    @FunctionalInterface
    public interface RequireUpdatedFlags<T> {
        T updatedFlags(ImmutableList<UpdatedFlags> updatedFlags);

        default T updatedFlags(Iterable<UpdatedFlags> updatedFlags) {
            return updatedFlags(ImmutableList.copyOf(updatedFlags));
        }

        default T updatedFlag(UpdatedFlags updatedFlags) {
            return updatedFlags(ImmutableList.of(updatedFlags));
        }
    }

    @FunctionalInterface
    public interface RequireQuotaRoot<T> {
        T quotaRoot(QuotaRoot quotaRoot);
    }

    @FunctionalInterface
    public interface RequireQuotaCountValue<T> {
        T quotaCount(QuotaCount quotaCount);
    }

    @FunctionalInterface
    public interface RequireQuotaSizeValue<T> {
        T quotaSize(QuotaSize quotaSize);
    }

    @FunctionalInterface
    public interface RequireQuotaCount<T> {
        T quotaCount(Quota<QuotaCount> quotaCount);
    }

    @FunctionalInterface
    public interface RequireQuotaSize<T> {
        T quotaSize(Quota<QuotaSize> quotaSize);
    }

    @FunctionalInterface
    public interface RequireInstant<T> {
        T instant(Instant instant);
    }

    @FunctionalInterface
    public interface RequireMailboxEvent<T> extends RequireEventId<RequireSession<RequireMailbox<T>>> {}

    public static class MailboxAddedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;

        MailboxAddedFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
        }

        public MailboxListener.MailboxAdded build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);

            return new MailboxListener.MailboxAdded(sessionId, user, path, mailboxId, eventId);
        }
    }

    public static class AddedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final ImmutableSortedMap<MessageUid, MessageMetaData> metaData;

        AddedFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, Map<MessageUid, MessageMetaData> metaData) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.metaData = ImmutableSortedMap.copyOf(metaData);
        }

        public MailboxListener.Added build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);
            Preconditions.checkNotNull(metaData);

            return new MailboxListener.Added(sessionId, user, path, mailboxId, metaData, eventId);
        }
    }

    public static class ExpungedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final ImmutableSortedMap<MessageUid, MessageMetaData> metaData;

        ExpungedFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, Map<MessageUid, MessageMetaData> metaData) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.metaData = ImmutableSortedMap.copyOf(metaData);
        }

        public MailboxListener.Expunged build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);
            Preconditions.checkNotNull(metaData);

            return new MailboxListener.Expunged(sessionId, user, path, mailboxId, metaData, eventId);
        }
    }

    public static class MailboxAclUpdatedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final ACLDiff aclDiff;

        MailboxAclUpdatedFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, ACLDiff aclDiff) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.aclDiff = aclDiff;
        }

        public MailboxListener.MailboxACLUpdated build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);
            Preconditions.checkNotNull(aclDiff);

            return new MailboxListener.MailboxACLUpdated(sessionId, user, path, aclDiff, mailboxId, eventId);
        }
    }

    public static class MailboxDeletionFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final QuotaRoot quotaRoot;
        private final QuotaCount deletedMessageCount;
        private final QuotaSize totalDeletedSize;

        MailboxDeletionFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, QuotaRoot quotaRoot, QuotaCount deletedMessageCount, QuotaSize totalDeletedSize) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.quotaRoot = quotaRoot;
            this.deletedMessageCount = deletedMessageCount;
            this.totalDeletedSize = totalDeletedSize;
        }

        public MailboxListener.MailboxDeletion build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);
            Preconditions.checkNotNull(quotaRoot);
            Preconditions.checkNotNull(deletedMessageCount);
            Preconditions.checkNotNull(totalDeletedSize);

            return new MailboxListener.MailboxDeletion(sessionId, user, path, quotaRoot, deletedMessageCount, totalDeletedSize, mailboxId, eventId);
        }
    }

    public static class MailboxRenamedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath oldPath;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final MailboxPath newPath;

        MailboxRenamedFinalStage(Event.EventId eventId, MailboxPath oldPath, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, MailboxPath newPath) {
            this.eventId = eventId;
            this.oldPath = oldPath;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.newPath = newPath;
        }


        public MailboxListener.MailboxRenamed build() {
            Preconditions.checkNotNull(oldPath);
            Preconditions.checkNotNull(newPath);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);

            return new MailboxListener.MailboxRenamed(sessionId, user, oldPath, mailboxId, newPath, eventId);
        }
    }

    public static class FlagsUpdatedFinalStage {
        private final Event.EventId eventId;
        private final MailboxPath path;
        private final MailboxId mailboxId;
        private final User user;
        private final MailboxSession.SessionId sessionId;
        private final ImmutableList<UpdatedFlags> updatedFlags;

        FlagsUpdatedFinalStage(Event.EventId eventId, MailboxPath path, MailboxId mailboxId, User user, MailboxSession.SessionId sessionId, ImmutableList<UpdatedFlags> updatedFlags) {
            this.eventId = eventId;
            this.path = path;
            this.mailboxId = mailboxId;
            this.user = user;
            this.sessionId = sessionId;
            this.updatedFlags = updatedFlags;
        }


        public MailboxListener.FlagsUpdated build() {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(mailboxId);
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(sessionId);
            Preconditions.checkNotNull(updatedFlags);

            return new MailboxListener.FlagsUpdated(sessionId, user, path, mailboxId, updatedFlags, eventId);
        }
    }

    public static final class QuotaUsageUpdatedFinalStage {
        private final Event.EventId eventId;
        private final User user;
        private final QuotaRoot quotaRoot;
        private final Quota<QuotaCount> countQuota;
        private final Quota<QuotaSize> sizeQuota;
        private final Instant instant;

        QuotaUsageUpdatedFinalStage(Event.EventId eventId, User user, QuotaRoot quotaRoot, Quota<QuotaCount> countQuota, Quota<QuotaSize> sizeQuota, Instant instant) {
            this.eventId = eventId;
            this.user = user;
            this.quotaRoot = quotaRoot;
            this.countQuota = countQuota;
            this.sizeQuota = sizeQuota;
            this.instant = instant;
        }

        public MailboxListener.QuotaUsageUpdatedEvent build() {
            return new MailboxListener.QuotaUsageUpdatedEvent(eventId, user, quotaRoot, countQuota, sizeQuota, instant);
        }
    }

    public static RequireMailboxEvent<RequireMetadata<AddedFinalStage>> added() {
        return eventId -> user -> sessionId -> mailboxId -> path -> metaData -> new AddedFinalStage(eventId, path, mailboxId, user, sessionId, metaData);
    }

    public static RequireMailboxEvent<RequireMetadata<ExpungedFinalStage>> expunged() {
        return eventId -> user -> sessionId -> mailboxId -> path -> metaData -> new ExpungedFinalStage(eventId, path, mailboxId, user, sessionId, metaData);
    }

    public static RequireMailboxEvent<RequireUpdatedFlags<FlagsUpdatedFinalStage>> flagsUpdated() {
        return eventId -> user -> sessionId -> mailboxId -> path -> updatedFlags -> new FlagsUpdatedFinalStage(eventId, path, mailboxId, user, sessionId, updatedFlags);
    }

    public static RequireEventId<RequireSession<RequireMailboxId<RequireOldPath<RequireNewPath<MailboxRenamedFinalStage>>>>> mailboxRenamed() {
        return eventId -> user -> sessionId -> mailboxId -> oldPath -> newPath -> new MailboxRenamedFinalStage(eventId, oldPath, mailboxId, user, sessionId, newPath);
    }

    public static  RequireMailboxEvent<RequireQuotaRoot<RequireQuotaCountValue<RequireQuotaSizeValue<MailboxDeletionFinalStage>>>> mailboxDeleted() {
        return eventId -> user -> sessionId -> mailboxId -> path -> quotaRoot -> quotaCount -> quotaSize -> new MailboxDeletionFinalStage(
            eventId, path, mailboxId, user, sessionId, quotaRoot, quotaCount, quotaSize);
    }

    public static RequireMailboxEvent<MailboxAddedFinalStage> mailboxAdded() {
        return eventId -> user -> sessionId -> mailboxId -> path -> new MailboxAddedFinalStage(eventId, path, mailboxId, user, sessionId);
    }

    public static RequireMailboxEvent<RequireAclDiff<MailboxAclUpdatedFinalStage>> aclUpdated() {
        return eventId -> user -> sessionId -> mailboxId -> path -> aclDiff -> new MailboxAclUpdatedFinalStage(eventId, path, mailboxId, user, sessionId, aclDiff);
    }

    public static RequireEventId<RequireUser<RequireQuotaRoot<RequireQuotaCount<RequireQuotaSize<RequireInstant<QuotaUsageUpdatedFinalStage>>>>>> quotaUpdated() {
        return eventId -> user -> quotaRoot -> quotaCount -> quotaSize -> instant -> new QuotaUsageUpdatedFinalStage(eventId, user, quotaRoot, quotaCount, quotaSize, instant);
    }

    public static MessageMoveEvent.Builder moved() {
        return MessageMoveEvent.builder();
    }
}
