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
import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class DeletedMessageWithStorageInformationConverter {
    private final BlobId.Factory blobFactory;
    private final MessageId.Factory messageIdFactory;
    private final MailboxId.Factory mailboxIdFactory;

    @Inject
    public DeletedMessageWithStorageInformationConverter(BlobId.Factory blobFactory,
                                                         MessageId.Factory messageIdFactory,
                                                         MailboxId.Factory mailboxIdFactory) {
        this.blobFactory = blobFactory;
        this.messageIdFactory = messageIdFactory;
        this.mailboxIdFactory = mailboxIdFactory;
    }

    public StorageInformation toDomainObject(DeletedMessageWithStorageInformationDTO.StorageInformationDTO storageInformationDTO) {
        return StorageInformation.builder()
            .bucketName(BucketName.of(storageInformationDTO.getBucketName()))
            .blobId(blobFactory.from(storageInformationDTO.getBlobId()));
    }

    public DeletedMessage toDomainObject(DeletedMessageWithStorageInformationDTO.DeletedMessageDTO deletedMessageDTO) throws AddressException {
        return DeletedMessage.builder()
            .messageId(messageIdFactory.fromString(deletedMessageDTO.getMessageId()))
            .originMailboxes(deserializeOriginMailboxes(deletedMessageDTO.getOriginMailboxes()))
            .user(Username.of(deletedMessageDTO.getOwner()))
            .deliveryDate(ZonedDateTime.parse(deletedMessageDTO.getDeliveryDate()))
            .deletionDate(ZonedDateTime.parse(deletedMessageDTO.getDeletionDate()))
            .sender(MaybeSender.getMailSender(deletedMessageDTO.getSender()))
            .recipients(deserializeRecipients(deletedMessageDTO.getRecipients()))
            .hasAttachment(deletedMessageDTO.getHasAttachment())
            .size(deletedMessageDTO.getSize())
            .subject(deletedMessageDTO.getSubject())
            .build();
    }

    public DeletedMessageWithStorageInformation toDomainObject(DeletedMessageWithStorageInformationDTO deletedMessageWithStorageInfoDTO) throws AddressException {
        return new DeletedMessageWithStorageInformation(
            toDomainObject(deletedMessageWithStorageInfoDTO.getDeletedMessage()),
            toDomainObject(deletedMessageWithStorageInfoDTO.getStorageInformation()));
    }

    private ImmutableList<MailboxId> deserializeOriginMailboxes(List<String> originMailboxes) {
        return originMailboxes.stream()
            .map(mailboxId -> mailboxIdFactory.fromString(mailboxId))
            .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<MailAddress> deserializeRecipients(List<String> recipients) throws AddressException {
        return recipients.stream()
            .map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }
}
