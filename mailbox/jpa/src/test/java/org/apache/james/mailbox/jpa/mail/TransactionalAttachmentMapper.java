/***************************************************************
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

package org.apache.james.mailbox.jpa.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import reactor.core.publisher.Mono;

public class TransactionalAttachmentMapper implements AttachmentMapper {
    private final JPAAttachmentMapper attachmentMapper;

    public TransactionalAttachmentMapper(JPAAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) throws AttachmentNotFoundException, IOException {
        return attachmentMapper.loadAttachmentContent(attachmentId);
    }

    @Override
    public Mono<InputStream> loadAttachmentContentReactive(AttachmentId attachmentId) {
        return attachmentMapper.executeReactive(attachmentMapper.loadAttachmentContentReactive(attachmentId));
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        return attachmentMapper.getAttachment(attachmentId);
    }

    @Override
    public Mono<AttachmentMetadata> getAttachmentReactive(AttachmentId attachmentId) {
        return attachmentMapper.executeReactive(attachmentMapper.getAttachmentReactive(attachmentId));
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds);
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachments(Collection<ParsedAttachment> attachments, MessageId ownerMessageId) throws MailboxException {
        return attachmentMapper.execute(() -> attachmentMapper.storeAttachments(attachments, ownerMessageId));
    }

    @Override
    public Mono<List<MessageAttachmentMetadata>> storeAttachmentsReactive(Collection<ParsedAttachment> attachments, MessageId ownerMessageId) {
        return attachmentMapper.executeReactive(attachmentMapper.storeAttachmentsReactive(attachments, ownerMessageId));
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        return attachmentMapper.getRelatedMessageIds(attachmentId);
    }
}
