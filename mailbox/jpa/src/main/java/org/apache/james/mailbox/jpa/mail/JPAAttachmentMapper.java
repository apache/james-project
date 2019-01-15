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

package org.apache.james.mailbox.jpa.mail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAAttachment;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class JPAAttachmentMapper extends JPATransactionalMapper implements AttachmentMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPAAttachmentMapper.class);


    public JPAAttachmentMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }


    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return Optional.ofNullable(getEntityManager().find(JPAAttachment.class, attachmentId.getId()))
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId())).toAttachment();
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        return ImmutableList.<Attachment>builder().addAll(
               attachmentIds.stream().distinct()
               .map(attachmentId -> getEntityManager().find(JPAAttachment.class, attachmentId.getId()))
                       .filter(Objects::nonNull)
                       .map(jpaAttachment ->  jpaAttachment.toAttachment())
               .collect(Collectors.toList())

        ).build();


    }


    @Override
    public void storeAttachmentForOwner(Attachment attachment, Username owner) throws MailboxException {
        Preconditions.checkArgument(owner != null);
        Preconditions.checkArgument(attachment != null);
        JPAAttachment jpaAttachment = getJpaAttachment(attachment);
        try {
            jpaAttachment.getOwners().add(owner.getValue());
            getEntityManager().getTransaction().begin();
            getEntityManager().merge(jpaAttachment);
            getEntityManager().getTransaction().commit();
        } catch (PersistenceException e) {
            LOGGER.warn("Persist attachment for owner met an exception", e);
            throw new MailboxException(" Store attachment for owner " + owner.getValue() + " failed", e);
        }
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException {

        Preconditions.checkArgument(attachments != null);
        Preconditions.checkArgument(ownerMessageId != null);
        try {
            attachments.stream()
                    .map(attachment -> getJpaAttachment(attachment))
                    .forEach(jpaAttachment -> {
                        jpaAttachment.getMessageIds().add(ownerMessageId.serialize());
                        getEntityManager().getTransaction().begin();
                        getEntityManager().merge(jpaAttachment);
                        getEntityManager().getTransaction().commit();
                    });
        } catch (PersistenceException e) {
            LOGGER.warn("Persist attachments with messageId met an exception ", e);
            throw new MailboxException("Store attachments for owner with message " + ownerMessageId.serialize() +" failed.", e);
        }


    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        Preconditions.checkArgument(attachmentId != null);
        JPAAttachment attachment = getEntityManager().find(JPAAttachment.class, attachmentId.getId());

        if(attachment!=null && attachment.getMessageIds() != null) {
            return ImmutableList.<MessageId>builder()
                    .addAll(
                            attachment.getMessageIds().stream().
                                    map(messageId -> new JPAMessageId.Factory().fromString(messageId)).collect(Collectors.toList())
                    ).build();
        } else {
            return new ArrayList<>();
        }

    }

    @Override
    public Collection<Username> getOwners(AttachmentId attachmentId) throws MailboxException {
        Preconditions.checkArgument(attachmentId != null);
        JPAAttachment attachment = getEntityManager().find(JPAAttachment.class, attachmentId.getId());
        if(attachment != null && attachment.getOwners() != null) {
            return ImmutableList.<Username>builder().addAll(
                    attachment.getOwners().stream()
                            .map(owner -> Username.fromRawValue(owner)).collect(Collectors.toList())
            ).build();
        } else {
            return new ArrayList<>();
        }
    }


    private JPAAttachment getJpaAttachment(Attachment attachment) {
        return Optional.ofNullable(getEntityManager().find(JPAAttachment.class, attachment.getAttachmentId().getId()))
                .orElse(JPAAttachment.from(attachment));
    }

}
