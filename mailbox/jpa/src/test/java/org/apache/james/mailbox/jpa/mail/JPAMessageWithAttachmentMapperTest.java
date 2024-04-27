/**************************************************************
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageAssert;
import org.apache.james.mailbox.store.mail.model.MessageWithAttachmentMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JPAMessageWithAttachmentMapperTest extends MessageWithAttachmentMapperTest {

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    @Override
    protected MapperProvider createMapperProvider() {
        return new JPAMapperProvider(JPA_TEST_CLUSTER);
    }

    @AfterEach
    void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Test
    @Override
    protected void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);

        AttachmentMetadata attachment = messageWith1Attachment.getAttachments().get(0).getAttachment();
        MessageAttachmentMetadata attachmentMetadata = messageWith1Attachment.getAttachments().get(0);
        List<MessageAttachmentMetadata> messageAttachments = retrievedMessageIterator.next().getAttachments();

        // JPA does not support MessageId
        assertThat(messageAttachments)
            .extracting(MessageAttachmentMetadata::getAttachment)
            .extracting("attachmentId", "size", "type")
            .containsExactlyInAnyOrder(
                tuple(attachment.getAttachmentId(), attachment.getSize(), attachment.getType())
            );
        assertThat(messageAttachments)
            .extracting(
                MessageAttachmentMetadata::getAttachmentId,
                MessageAttachmentMetadata::getName,
                MessageAttachmentMetadata::getCid,
                MessageAttachmentMetadata::isInline
            )
            .containsExactlyInAnyOrder(
                tuple(attachmentMetadata.getAttachmentId(), attachmentMetadata.getName(), attachmentMetadata.getCid(), attachmentMetadata.isInline())
            );
    }

    @Test
    @Override
    protected void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenTwoAttachments() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith2Attachments.getUid()), fetchType, LIMIT);

        AttachmentMetadata attachment1 = messageWith2Attachments.getAttachments().get(0).getAttachment();
        AttachmentMetadata attachment2 = messageWith2Attachments.getAttachments().get(1).getAttachment();
        MessageAttachmentMetadata attachmentMetadata1 = messageWith2Attachments.getAttachments().get(0);
        MessageAttachmentMetadata attachmentMetadata2 = messageWith2Attachments.getAttachments().get(1);
        List<MessageAttachmentMetadata> messageAttachments = retrievedMessageIterator.next().getAttachments();

        // JPA does not support MessageId
        assertThat(messageAttachments)
            .extracting(MessageAttachmentMetadata::getAttachment)
            .extracting("attachmentId", "size", "type")
            .containsExactlyInAnyOrder(
                tuple(attachment1.getAttachmentId(), attachment1.getSize(), attachment1.getType()),
                tuple(attachment2.getAttachmentId(), attachment2.getSize(), attachment2.getType())
            );
        assertThat(messageAttachments)
            .extracting(
                MessageAttachmentMetadata::getAttachmentId,
                MessageAttachmentMetadata::getName,
                MessageAttachmentMetadata::getCid,
                MessageAttachmentMetadata::isInline
            )
            .containsExactlyInAnyOrder(
                tuple(attachmentMetadata1.getAttachmentId(), attachmentMetadata1.getName(), attachmentMetadata1.getCid(), attachmentMetadata1.isInline()),
                tuple(attachmentMetadata2.getAttachmentId(), attachmentMetadata2.getName(), attachmentMetadata2.getCid(), attachmentMetadata2.isInline())
            );
    }

    @Test
    @Override
    protected void messagesCanBeRetrievedInMailboxWithRangeTypeOne() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;

        // JPA does not support MessageId
        MessageAssert.assertThat(messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT).next())
            .isEqualToWithoutAttachment(messageWith1Attachment, fetchType);
    }
}
