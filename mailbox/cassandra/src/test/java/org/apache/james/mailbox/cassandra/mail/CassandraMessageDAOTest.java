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

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

public class CassandraMessageDAOTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);

    private CassandraCluster cassandra;

    private CassandraMessageDAO testee;
    private CassandraBlobsDAO blobsDAO;
    private CassandraMessageId.Factory messageIdFactory;

    private SimpleMailboxMessage message;
    private CassandraMessageId messageId;
    private ComposedMessageId composedMessageId;
    private List<ComposedMessageIdWithMetaData> messageIds;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraModuleComposite(new CassandraMessageModule(), new CassandraBlobModule()));
        cassandra.ensureAllTables();

        messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();
        blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        testee = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), blobsDAO);

        composedMessageId = new ComposedMessageId(MAILBOX_ID, messageId, messageUid);

        messageIds = ImmutableList.of(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build());
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void saveShouldSaveNullValueForTextualLineCountAsZero() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder());

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount())
            .isEqualTo(0L);
    }

    @Test
    public void saveShouldSaveTextualLineCount() throws Exception {
        long textualLineCount = 10L;
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        message = createMessage(messageId, CONTENT, BODY_START, propertyBuilder);

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    public void saveShouldStoreMessageWithFullContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder());

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Full, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT);
    }

    @Test
    public void saveShouldStoreMessageWithBodyContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder());

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Body, Limit.unlimited()));

        byte[] expected = Bytes.concat(
            new byte[BODY_START],
            CONTENT.substring(BODY_START).getBytes(Charsets.UTF_8));
        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), Charsets.UTF_8))
            .isEqualTo(IOUtils.toString(new ByteArrayInputStream(expected), Charsets.UTF_8));
    }

    @Test
    public void saveShouldStoreMessageWithHeaderContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder());

        testee.save(message).join();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Headers, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), Charsets.UTF_8))
            .isEqualTo(CONTENT.substring(0, BODY_START));
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new SharedByteArrayInputStream(content.getBytes(Charsets.UTF_8)))
            .flags(new Flags())
            .propertyBuilder(propertyBuilder)
            .build();
    }

    private MessageWithoutAttachment toMessage(CompletableFuture<Stream<CassandraMessageDAO.MessageResult>> readOptional) throws InterruptedException, java.util.concurrent.ExecutionException {
        return readOptional.join()
            .map(CassandraMessageDAO.MessageResult::message)
            .map(Pair::getLeft)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("Collection is not supposed to be empty"));
    }

}