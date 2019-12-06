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

package org.apache.james.mailbox.store;

import java.io.InputStream;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.BlobNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Blob;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;

import com.github.fge.lambdas.Throwing;

public class StoreBlobManager implements BlobManager {
    public static final String MESSAGE_RFC822_CONTENT_TYPE = "message/rfc822";
    private final AttachmentManager attachmentManager;
    private final MessageIdManager messageIdManager;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public StoreBlobManager(AttachmentManager attachmentManager, MessageIdManager messageIdManager,
                            MessageId.Factory messageIdFactory) {
        this.attachmentManager = attachmentManager;
        this.messageIdManager = messageIdManager;
        this.messageIdFactory = messageIdFactory;
    }

    @Override
    public BlobId toBlobId(MessageId messageId) {
        return BlobId.fromString(messageId.serialize());
    }

    @Override
    public Blob retrieve(BlobId blobId, MailboxSession mailboxSession) throws MailboxException, BlobNotFoundException {
        return getBlobFromAttachment(blobId, mailboxSession)
                .orElseGet(() -> getBlobFromMessage(blobId, mailboxSession)
                .orElseThrow(() -> new BlobNotFoundException(blobId)));
    }

    private Optional<Blob> getBlobFromAttachment(BlobId blobId, MailboxSession mailboxSession) throws MailboxException {
        try {
            AttachmentId attachmentId = AttachmentId.from(blobId);
            return Optional.of(attachmentManager.getAttachment(attachmentId, mailboxSession).toBlob());
        } catch (AttachmentNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<Blob> getBlobFromMessage(BlobId blobId, MailboxSession mailboxSession) {
        return retrieveMessageId(blobId)
                .flatMap(messageId -> loadMessageAsBlob(messageId, mailboxSession))
                .map(Throwing.function(
                    blob -> Blob.builder()
                        .id(blobId)
                        .contentType(MESSAGE_RFC822_CONTENT_TYPE)
                        .payload(IOUtils.toByteArray(blob))
                        .build()));
    }

    private Optional<MessageId> retrieveMessageId(BlobId blobId) {
        try {
            return Optional.of(messageIdFactory.fromString(blobId.asString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<InputStream> loadMessageAsBlob(MessageId messageId, MailboxSession mailboxSession)  {
        try {
            return messageIdManager.getMessage(messageId, FetchGroup.FULL_CONTENT, mailboxSession)
                .stream()
                .map(Throwing.function(message -> message.getFullContent().getInputStream()))
                .findFirst();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

}
