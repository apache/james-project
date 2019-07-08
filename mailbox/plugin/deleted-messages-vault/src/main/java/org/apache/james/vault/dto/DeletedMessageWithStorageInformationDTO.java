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

package org.apache.james.vault.dto;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class DeletedMessageWithStorageInformationDTO {

    public static class StorageInformationDTO {

        public static StorageInformationDTO toDTO(StorageInformation storageInformation) {
            return new StorageInformationDTO(storageInformation.getBucketName().asString(),
                storageInformation.getBlobId().asString());
        }

        private final String bucketName;
        private final String blobId;

        @JsonCreator
        public StorageInformationDTO(@JsonProperty("bucketName") String bucketName,
                                     @JsonProperty("blobId") String blobId) {
            Preconditions.checkNotNull(bucketName);
            Preconditions.checkNotNull(blobId);

            this.bucketName = bucketName;
            this.blobId = blobId;
        }

        public String getBucketName() {
            return bucketName;
        }

        public String getBlobId() {
            return blobId;
        }
    }

    public static class DeletedMessageDTO {

        public static DeletedMessageDTO toDTO(DeletedMessage deletedMessage) {
            return new DeletedMessageDTO(
                deletedMessage.getMessageId().serialize(),
                serializeOriginMailboxes(deletedMessage.getOriginMailboxes()),
                deletedMessage.getOwner().asString(),
                serializeZonedDateTime(deletedMessage.getDeliveryDate()),
                serializeZonedDateTime(deletedMessage.getDeletionDate()),
                deletedMessage.getSender().asString(),
                serializeRecipients(deletedMessage.getRecipients()),
                deletedMessage.getSubject(),
                deletedMessage.hasAttachment(),
                deletedMessage.getSize());
        }

        private static ImmutableList<String> serializeOriginMailboxes(List<MailboxId> originMailboxes) {
            return originMailboxes.stream()
                .map(MailboxId::serialize)
                .collect(Guavate.toImmutableList());
        }

        private static ImmutableList<String> serializeRecipients(List<MailAddress> recipients) {
            return recipients.stream()
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList());
        }

        private static String serializeZonedDateTime(ZonedDateTime time) {
            return time.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        }

        private final String messageId;
        private final List<String> originMailboxes;
        private final String owner;
        private final String deliveryDate;
        private final String deletionDate;
        private final String sender;
        private final List<String> recipients;
        private final Optional<String> subject;
        private final boolean hasAttachment;
        private final long size;

        @JsonCreator
        public DeletedMessageDTO(@JsonProperty("messageId") String messageId,
                                 @JsonProperty("originMailboxes") List<String> originMailboxes,
                                 @JsonProperty("owner") String owner,
                                 @JsonProperty("deliveryDate") String deliveryDate,
                                 @JsonProperty("deletionDate") String deletionDate,
                                 @JsonProperty("sender") String sender,
                                 @JsonProperty("recipients") List<String> recipients,
                                 @JsonProperty("subject") Optional<String> subject,
                                 @JsonProperty("hasAttachment") boolean hasAttachment,
                                 @JsonProperty("size") long size) {
            this.messageId = messageId;
            this.originMailboxes = originMailboxes;
            this.owner = owner;
            this.deliveryDate = deliveryDate;
            this.deletionDate = deletionDate;
            this.sender = sender;
            this.recipients = recipients;
            this.subject = subject;
            this.hasAttachment = hasAttachment;
            this.size = size;
        }

        public String getMessageId() {
            return messageId;
        }

        public List<String> getOriginMailboxes() {
            return originMailboxes;
        }

        public String getOwner() {
            return owner;
        }

        public String getDeliveryDate() {
            return deliveryDate;
        }

        public String getDeletionDate() {
            return deletionDate;
        }

        public String getSender() {
            return sender;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public Optional<String> getSubject() {
            return subject;
        }

        public boolean getHasAttachment() {
            return hasAttachment;
        }

        public long getSize() {
            return size;
        }
    }

    public static DeletedMessageWithStorageInformationDTO toDTO(DeletedMessageWithStorageInformation deletedMessageWithStorageInformation) {
        return new DeletedMessageWithStorageInformationDTO(
            DeletedMessageDTO.toDTO(deletedMessageWithStorageInformation.getDeletedMessage()),
            StorageInformationDTO.toDTO(deletedMessageWithStorageInformation.getStorageInformation()));
    }

    private final DeletedMessageDTO deletedMessage;
    private final StorageInformationDTO storageInformation;

    @JsonCreator
    public DeletedMessageWithStorageInformationDTO(@JsonProperty("deletedMessage") DeletedMessageDTO deletedMessage,
                                                   @JsonProperty("storageInformation") StorageInformationDTO storageInformation) {
        this.deletedMessage = deletedMessage;
        this.storageInformation = storageInformation;
    }


    public DeletedMessageDTO getDeletedMessage() {
        return deletedMessage;
    }

    public StorageInformationDTO getStorageInformation() {
        return storageInformation;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("messageId", deletedMessage.messageId)
            .add("originMailboxes", deletedMessage.originMailboxes)
            .add("owner", deletedMessage.owner)
            .add("deliveryDate", deletedMessage.deliveryDate)
            .add("deletionDate", deletedMessage.deletionDate)
            .add("sender", deletedMessage.sender)
            .add("recipients", deletedMessage.recipients)
            .add("hasAttachment", deletedMessage.hasAttachment)
            .add("size", deletedMessage.size)
            .add("subject", deletedMessage.subject)
            .add("bucketName", storageInformation.bucketName)
            .add("blobId", storageInformation.blobId)
            .toString();
    }

}
