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

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV2;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.OptionalConverter;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class V1ToV2MigrationTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);

    private CassandraCluster cassandra;

    private CassandraMessageDAO messageDAOV1;
    private CassandraMessageDAOV2 messageDAOV2;
    private CassandraAttachmentMapper attachmentMapper;
    private V1ToV2Migration testee;

    private Attachment attachment;
    private CassandraMessageId messageId;
    private CassandraMessageId.Factory messageIdFactory;
    private ComposedMessageId composedMessageId;
    private List<ComposedMessageIdWithMetaData> metaDataList;
    private ComposedMessageIdWithMetaData metaData;
    private MessageAttachment messageAttachment;
    
    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();
    private ConditionFactory awaitability;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraModuleComposite(
            new CassandraMessageModule(),
            new CassandraBlobModule(),
            new CassandraAttachmentModule()));
        cassandra.ensureAllTables();

        messageDAOV1 = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider());
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        messageDAOV2 = new CassandraMessageDAOV2(cassandra.getConf(), cassandra.getTypesProvider(), blobsDAO);
        attachmentMapper = new CassandraAttachmentMapper(cassandra.getConf());
        testee = new V1ToV2Migration(messageDAOV1, messageDAOV2, attachmentMapper, CassandraConfiguration.builder()
            .onTheFlyV1ToV2Migration(true)
            .build());


        messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();

        attachment = Attachment.builder()
                .attachmentId(AttachmentId.from("123"))
                .bytes("attachment".getBytes())
                .type("content")
                .build();

        composedMessageId = new ComposedMessageId(MAILBOX_ID, messageId, messageUid);

        metaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(composedMessageId)
            .flags(new Flags())
            .modSeq(1)
            .build();
        metaDataList = ImmutableList.of(metaData);
        messageAttachment = MessageAttachment.builder()
            .attachment(attachment)
            .cid(Cid.from("<cid>"))
            .isInline(true)
            .name("toto.png")
            .build();

        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        awaitability = Awaitility
            .with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .pollDelay(slowPacedPollInterval).await();
    }

    @After
    public void tearDown() {
        testee.stop();
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void migrationShouldWorkWithoutAttachments() throws Exception {
        SimpleMailboxMessage originalMessage = createMessage(messageId, CONTENT, BODY_START,
            new PropertyBuilder(), ImmutableList.of());
        messageDAOV1.save(originalMessage).join();

        testee.getFromV2orElseFromV1AfterMigration(CassandraMessageDAOV2.notFound(metaData)).join();

        awaitMigration();

        CassandraMessageDAOV2.MessageResult messageResult = retrieveMessageOnV2().get();
        softly.assertThat(messageResult.message().getLeft().getMessageId()).isEqualTo(messageId);
        softly.assertThat(IOUtils.toString(messageResult.message().getLeft().getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT);
        softly.assertThat(messageResult.message().getRight().findAny().isPresent()).isFalse();
    }

    @Test
    public void runShouldMigrateMessages() throws Exception {
        SimpleMailboxMessage originalMessage = createMessage(messageId, CONTENT, BODY_START,
            new PropertyBuilder(), ImmutableList.of());
        messageDAOV1.save(originalMessage).join();

        testee.run();

        CassandraMessageDAOV2.MessageResult messageResult = retrieveMessageOnV2().get();
        softly.assertThat(messageResult.message().getLeft().getMessageId()).isEqualTo(messageId);
        softly.assertThat(IOUtils.toString(messageResult.message().getLeft().getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT);
        softly.assertThat(messageResult.message().getRight().findAny().isPresent()).isFalse();
    }

    @Test
    public void runShouldBeIdempotent() throws Exception {
        SimpleMailboxMessage originalMessage = createMessage(messageId, CONTENT, BODY_START,
            new PropertyBuilder(), ImmutableList.of());
        messageDAOV1.save(originalMessage).join();

        testee.run();

        testee.run();

        CassandraMessageDAOV2.MessageResult messageResult = retrieveMessageOnV2().get();
        softly.assertThat(messageResult.message().getLeft().getMessageId()).isEqualTo(messageId);
        softly.assertThat(IOUtils.toString(messageResult.message().getLeft().getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT);
        softly.assertThat(messageResult.message().getRight().findAny().isPresent()).isFalse();
    }

    @Test
    public void runShouldSucceedWhenOneMessage() throws Exception {
        SimpleMailboxMessage originalMessage = createMessage(messageId, CONTENT, BODY_START,
            new PropertyBuilder(), ImmutableList.of());
        messageDAOV1.save(originalMessage).join();

        assertThat(testee.run()).isTrue();
    }

    @Test
    public void runShouldSucceedWhenNoMessages() throws Exception {
        assertThat(testee.run()).isTrue();
    }

    @Test
    public void migrationShouldWorkWithAttachments() throws Exception {
        SimpleMailboxMessage originalMessage = createMessage(messageId, CONTENT, BODY_START,
            new PropertyBuilder(), ImmutableList.of(messageAttachment));

        attachmentMapper.storeAttachment(attachment);

        messageDAOV1.save(originalMessage).join();

        testee.getFromV2orElseFromV1AfterMigration(CassandraMessageDAOV2.notFound(metaData)).join();

        awaitMigration();

        CassandraMessageDAOV2.MessageResult messageResult = retrieveMessageOnV2().get();
        softly.assertThat(messageResult.message().getLeft().getMessageId()).isEqualTo(messageId);
        softly.assertThat(IOUtils.toString(messageResult.message().getLeft().getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT);
        softly.assertThat(messageResult.message().getRight().findAny().get()).isEqualTo(MessageAttachmentRepresentation.builder()
            .attachmentId(attachment.getAttachmentId())
            .cid(OptionalConverter.fromGuava(messageAttachment.getCid()))
            .isInline(messageAttachment.isInline())
            .name(messageAttachment.getName().get())
            .build());
    }

    private void awaitMigration() {
        awaitability.atMost(1, TimeUnit.MINUTES)
            .until(() -> {
                try {
                    retrieveMessageOnV2();
                    return true;
                } catch(AssertionError e) {
                    return false;
                }
            });
    }

    private Optional<CassandraMessageDAOV2.MessageResult> retrieveMessageOnV2() {
        Optional<CassandraMessageDAOV2.MessageResult> messageResult = messageDAOV2.retrieveMessages(metaDataList, MessageMapper.FetchType.Full, Limit.unlimited())
            .join()
            .findAny();

        assertThat(messageResult.isPresent()).isTrue();
        return messageResult;
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments) {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, MAILBOX_ID, attachments);
    }

}