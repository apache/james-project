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

package org.apache.james.vault;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.DeletedMessage.Builder.FinalStage;
import org.apache.james.vault.DeletedMessage.Builder.Steps.RequireMetadata;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class DeletedMessage {
    public static class Builder {
        @FunctionalInterface
        public interface RequireMessageId<T> {
            T messageId(MessageId messageId);
        }

        @FunctionalInterface
        public interface RequireUser<T> {
            T user(User user);
        }

        @FunctionalInterface
        public interface RequireOriginMailboxes<T> {
            T originMailboxes(List<MailboxId> mailboxIds);

            default T originMailboxes(MailboxId... mailboxIds) {
                return originMailboxes(ImmutableList.copyOf(mailboxIds));
            }
        }

        @FunctionalInterface
        public interface RequireDeliveryDate<T> {
            T deliveryDate(ZonedDateTime deliveryDate);
        }

        @FunctionalInterface
        public interface RequireDeletionDate<T> {
            T deletionDate(ZonedDateTime deletionDate);
        }

        @FunctionalInterface
        public interface RequireSender<T> {
            T sender(MaybeSender sender);
        }

        @FunctionalInterface
        public interface RequireRecipients<T> {
            T recipients(Collection<MailAddress> recipients);

            default T recipients(MailAddress... recipients) {
                return recipients(ImmutableList.copyOf(recipients));
            }
        }

        @FunctionalInterface
        public interface RequireHasAttachment<T> {
            T hasAttachment(boolean value);

            default T hasAttachment() {
                return hasAttachment(true);
            }

            default T hasNoAttachments() {
                return hasAttachment(false);
            }
        }

        interface Steps {
            interface RequireMailboxContext<T> extends RequireMessageId<RequireOriginMailboxes<RequireUser<T>>> {}

            interface RequireEnvelope<T> extends RequireSender<RequireRecipients<T>> {}

            interface RequireDates<T> extends RequireDeliveryDate<RequireDeletionDate<T>> {}

            interface RequireMetadata<T> extends RequireMailboxContext<RequireDates<RequireEnvelope<RequireHasAttachment<T>>>> {}
        }

        public static class FinalStage {
            private final MessageId messageId;
            private final List<MailboxId> originMailboxes;
            private final User owner;
            private final ZonedDateTime deliveryDate;
            private final ZonedDateTime deletionDate;
            private final MaybeSender sender;
            private final List<MailAddress> recipients;
            private final boolean hasAttachment;
            private Optional<String> subject;

            FinalStage(MessageId messageId, List<MailboxId> originMailboxes, User owner, ZonedDateTime deliveryDate,
                       ZonedDateTime deletionDate, MaybeSender sender, List<MailAddress> recipients, boolean hasAttachment) {
                this.messageId = messageId;
                this.originMailboxes = originMailboxes;
                this.owner = owner;
                this.deliveryDate = deliveryDate;
                this.deletionDate = deletionDate;
                this.sender = sender;
                this.recipients = recipients;
                this.hasAttachment = hasAttachment;
                this.subject = Optional.empty();
            }

            public FinalStage subject(String subject) {
                this.subject = Optional.of(subject);
                return this;
            }

            public FinalStage subject(Optional<String> subject) {
                this.subject = subject;
                return this;
            }

            public DeletedMessage build() {
                return new DeletedMessage(messageId, originMailboxes, owner, deliveryDate, deletionDate, sender,
                    recipients, subject, hasAttachment);
            }
        }
    }

    public static RequireMetadata<FinalStage> builder() {
        return messageId -> originMailboxes -> user -> deliveryDate -> deletionDate -> sender -> recipients -> hasAttachment ->
            new Builder.FinalStage(messageId, originMailboxes, user, deliveryDate, deletionDate, sender, ImmutableList.copyOf(recipients), hasAttachment);
    }

    private final MessageId messageId;
    private final List<MailboxId> originMailboxes;
    private final User owner;
    private final ZonedDateTime deliveryDate;
    private final ZonedDateTime deletionDate;
    private final MaybeSender sender;
    private final List<MailAddress> recipients;
    private final Optional<String> subject;
    private final boolean hasAttachment;

    public DeletedMessage(MessageId messageId, List<MailboxId> originMailboxes, User owner,
                          ZonedDateTime deliveryDate, ZonedDateTime deletionDate, MaybeSender sender, List<MailAddress> recipients,
                          Optional<String> subject, boolean hasAttachment) {
        this.messageId = messageId;
        this.originMailboxes = originMailboxes;
        this.owner = owner;
        this.deliveryDate = deliveryDate;
        this.deletionDate = deletionDate;
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.hasAttachment = hasAttachment;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public List<MailboxId> getOriginMailboxes() {
        return originMailboxes;
    }

    public User getOwner() {
        return owner;
    }

    public ZonedDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public ZonedDateTime getDeletionDate() {
        return deletionDate;
    }

    public MaybeSender getSender() {
        return sender;
    }

    public List<MailAddress> getRecipients() {
        return recipients;
    }

    public Optional<String> getSubject() {
        return subject;
    }

    public boolean hasAttachment() {
        return hasAttachment;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DeletedMessage) {
            DeletedMessage that = (DeletedMessage) o;

            return Objects.equals(this.hasAttachment, that.hasAttachment)
                && Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.originMailboxes, that.originMailboxes)
                && Objects.equals(this.owner, that.owner)
                && Objects.equals(this.deliveryDate, that.deliveryDate)
                && Objects.equals(this.deletionDate, that.deletionDate)
                && Objects.equals(this.sender, that.sender)
                && Objects.equals(this.recipients, that.recipients)
                && Objects.equals(this.subject, that.subject);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageId, originMailboxes, owner, deliveryDate, deletionDate, sender, recipients,
            subject, hasAttachment);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("owner", owner)
            .add("messageId", messageId)
            .toString();
    }
}
