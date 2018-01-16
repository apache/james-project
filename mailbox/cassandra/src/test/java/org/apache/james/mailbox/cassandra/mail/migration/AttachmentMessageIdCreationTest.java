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

package org.apache.james.mailbox.cassandra.mail.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AttachmentMessageIdCreationTest {
    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private CassandraBlobsDAO blobsDAO;
    private CassandraMessageDAO cassandraMessageDAO;
    private CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;

    private AttachmentMessageIdCreation migration;

    private SimpleMailboxMessage message;
    private CassandraMessageId messageId;

    @Before
    public void setUp() {
        CassandraCluster cassandra = CassandraCluster.create(
            new CassandraModuleComposite(
                new CassandraMessageModule(),
                new CassandraAttachmentModule(),
                new CassandraBlobModule()),
            cassandraServer.getIp(),
            cassandraServer.getBindingPort());
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();

        blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        cassandraMessageDAO = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            blobsDAO, CassandraUtils.WITH_DEFAULT_CONFIGURATION, messageIdFactory);

        attachmentMessageIdDAO = new CassandraAttachmentMessageIdDAO(cassandra.getConf(),
            new CassandraMessageId.Factory(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);

        migration = new AttachmentMessageIdCreation(cassandraMessageDAO, attachmentMessageIdDAO);

        messageId = messageIdFactory.generate();
    }

    @Test
    public void emptyMigrationShouldSucceed() {
        assertThat(migration.run())
            .isEqualTo(Migration.Result.COMPLETED);
    }

    @Test
    public void migrationShouldSucceedWhenNoAttachment() throws Exception {
        List<MessageAttachment> noAttachment = ImmutableList.of();
        message = createMessage(messageId, noAttachment);

        cassandraMessageDAO.save(message).join();

        assertThat(migration.run())
            .isEqualTo(Migration.Result.COMPLETED);
    }

    @Test
    public void migrationShouldSucceedWhenAttachment() throws Exception {
        MessageAttachment attachment = createAttachment();
        message = createMessage(messageId, ImmutableList.of(attachment));

        cassandraMessageDAO.save(message).join();

        assertThat(migration.run())
            .isEqualTo(Migration.Result.COMPLETED);
    }

    @Test
    public void migrationShouldCreateAttachmentIdOnAttachmentMessageIdTableFromMessage() throws Exception {
        MessageAttachment attachment = createAttachment();
        message = createMessage(messageId, ImmutableList.of(attachment));

        cassandraMessageDAO.save(message).join();

        migration.run();

        assertThat(attachmentMessageIdDAO.getOwnerMessageIds(attachment.getAttachmentId()).join())
            .containsExactly(messageId);
    }

    @Test
    public void migrationShouldReturnPartialWhenRetrieveAllAttachmentIdFromMessageFail() throws Exception {
        CassandraMessageDAO cassandraMessageDAO = mock(CassandraMessageDAO.class);
        CassandraAttachmentMessageIdDAO attachmentMessageIdDAO = mock(CassandraAttachmentMessageIdDAO.class);
        migration = new AttachmentMessageIdCreation(cassandraMessageDAO, attachmentMessageIdDAO);

        when(cassandraMessageDAO.retrieveAllMessageIdAttachmentIds()).thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    @Test
    public void migrationShouldReturnPartialWhenSavingAttachmentIdForMessageIdFail() throws Exception {
        CassandraMessageDAO cassandraMessageDAO = mock(CassandraMessageDAO.class);
        CassandraAttachmentMessageIdDAO attachmentMessageIdDAO = mock(CassandraAttachmentMessageIdDAO.class);
        CassandraMessageDAO.MessageIdAttachmentIds messageIdAttachmentIds = mock(CassandraMessageDAO.MessageIdAttachmentIds.class);

        migration = new AttachmentMessageIdCreation(cassandraMessageDAO, attachmentMessageIdDAO);

        when(messageIdAttachmentIds.getAttachmentId()).thenReturn(ImmutableSet.of(AttachmentId.from("any")));
        when(cassandraMessageDAO.retrieveAllMessageIdAttachmentIds())
            .thenReturn(CompletableFuture.completedFuture(Stream.of(messageIdAttachmentIds)));
        when(attachmentMessageIdDAO.storeAttachmentForMessageId(any(AttachmentId.class), any(MessageId.class)))
            .thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, Collection<MessageAttachment> attachments) {
        MessageUid messageUid = MessageUid.of(1);
        CassandraId mailboxId = CassandraId.timeBased();
        String content = "Subject: Any subject \n\nThis is the body\n.\n";
        int bodyStart = 22;

        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(mailboxId)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new SharedByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .addAttachments(attachments)
            .build();
    }

    private MessageAttachment createAttachment() {
        return MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
    }
}