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

package org.apache.james.mailbox.postgres.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.postgres.JPATransactionalMapper;
import org.apache.james.mailbox.postgres.mail.model.JPAAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class JPAAttachmentMapper extends JPATransactionalMapper implements AttachmentMapper {

    private static final String ID_PARAM = "idParam";

    public JPAAttachmentMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return getEntityManager().createNamedQuery("findAttachmentById", JPAAttachment.class)
            .setParameter(ID_PARAM, attachmentId.getId())
            .getSingleResult().getContent();
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        AttachmentMetadata attachmentMetadata = getAttachmentMetadata(attachmentId);
        if (attachmentMetadata == null) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentMetadata;
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        ImmutableList.Builder<AttachmentMetadata> builder = ImmutableList.builder();
        for (AttachmentId attachmentId : attachmentIds) {
            AttachmentMetadata attachmentMetadata = getAttachmentMetadata(attachmentId);
            if (attachmentMetadata != null) {
                builder.add(attachmentMetadata);
            }
        }
        return builder.build();
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachments(Collection<ParsedAttachment> parsedAttachments, MessageId ownerMessageId) {
        Preconditions.checkArgument(parsedAttachments != null);
        Preconditions.checkArgument(ownerMessageId != null);
        return parsedAttachments.stream()
            .map(Throwing.<ParsedAttachment, MessageAttachmentMetadata>function(
                    typedContent -> storeAttachmentForMessage(ownerMessageId, typedContent))
                .sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }

    private AttachmentMetadata getAttachmentMetadata(AttachmentId attachmentId) {
        try {
            return getEntityManager().createNamedQuery("findAttachmentById", JPAAttachment.class)
                .setParameter(ID_PARAM, attachmentId.getId())
                .getSingleResult()
                .toAttachmentMetadata();
        } catch (NoResultException e) {
            return null;
        }
    }

    private MessageAttachmentMetadata storeAttachmentForMessage(MessageId ownerMessageId, ParsedAttachment parsedAttachment) throws MailboxException {
        try {
            byte[] bytes = IOUtils.toByteArray(parsedAttachment.getContent().openStream());
            JPAAttachment persistedAttachment = new JPAAttachment(parsedAttachment.asMessageAttachment(AttachmentId.random(), ownerMessageId), bytes);
            getEntityManager().persist(persistedAttachment);
            AttachmentId attachmentId = AttachmentId.from(persistedAttachment.getAttachmentId());
            return parsedAttachment.asMessageAttachment(attachmentId, bytes.length, ownerMessageId);
        } catch (IOException e) {
            throw new MailboxException("Failed to store attachment for message " + ownerMessageId, e);
        }
    }
}
