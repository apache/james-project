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
package org.apache.james.mailbox.elasticsearch.events;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.easymock.IMocksControl;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;

public class ElasticSearchListeningMessageSearchIndexTest {

    public static final long MODSEQ = 18L;
    private IMocksControl control;
    
    private MessageMapperFactory<TestId> mapperFactory;
    private ElasticSearchIndexer indexer;
    private MessageToElasticSearchJson messageToElasticSearchJson;
    private ElasticSearchSearcher<TestId> elasticSearchSearcher;
    
    private ElasticSearchListeningMessageSearchIndex<TestId> testee;
    
    @Before
    @SuppressWarnings("unchecked")
    public void setup() throws JsonProcessingException {
        control = createControl();
        
        mapperFactory = control.createMock(MessageMapperFactory.class);
        indexer = control.createMock(ElasticSearchIndexer.class);
        messageToElasticSearchJson = control.createMock(MessageToElasticSearchJson.class);
        expect(messageToElasticSearchJson.convertToJson(anyObject(Message.class))).andReturn("json content").anyTimes();
        expect(messageToElasticSearchJson.getUpdatedJsonMessagePart(anyObject(Flags.class), anyLong())).andReturn("json updated content").anyTimes();
        
        elasticSearchSearcher = control.createMock(ElasticSearchSearcher.class);

        testee = new ElasticSearchListeningMessageSearchIndex<>(mapperFactory, indexer, elasticSearchSearcher, messageToElasticSearchJson);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void addShouldIndex() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);
        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        
        long messageId = 1;
        TestId mailboxId = TestId.of(12);
        expect(mailbox.getMailboxId()).andReturn(mailboxId);
        Message<TestId> message = mockedMessage(messageId, mailboxId);
        
        IndexResponse expectedIndexResponse = control.createMock(IndexResponse.class);
        expect(indexer.indexMessage(eq(mailboxId.serialize() + ":" + messageId), anyString()))
            .andReturn(expectedIndexResponse);
        
        control.replay();
        testee.add(session, mailbox, message);
        control.verify();
    }

    @SuppressWarnings("unchecked")
    private Message<TestId> mockedMessage(long messageId, TestId mailboxId) throws IOException {
        Message<TestId> message = control.createMock(Message.class);
        expect(message.getUid()).andReturn(messageId).anyTimes();
        return message;
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void addShouldNotPropagateExceptionWhenExceptionOccurs() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);
        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        
        long messageId = 1;
        TestId mailboxId = TestId.of(12);
        Message<TestId> message = mockedMessage(messageId, mailboxId);
        expect(mailbox.getMailboxId()).andReturn(mailboxId);
        
        expect(indexer.indexMessage(eq(mailboxId.serialize() + ":" + messageId), anyString()))
            .andThrow(new ElasticsearchException(""));
        
        control.replay();
        testee.add(session, mailbox, message);
        control.verify();
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void deleteShouldWork() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);
        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        long messageId = 1;
        MessageRange messageRange = MessageRange.one(messageId);
        TestId mailboxId = TestId.of(12);
        expect(mailbox.getMailboxId()).andReturn(mailboxId);
        
        DeleteResponse expectedDeleteResponse = control.createMock(DeleteResponse.class);
        expect(indexer.deleteMessage(mailboxId.serialize() + ":" + messageId))
            .andReturn(expectedDeleteResponse);
        
        control.replay();
        testee.delete(session, mailbox, messageRange);
        control.verify();
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void deleteShouldWorkWhenMultipleMessageIds() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);
        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        long firstMessageId = 1;
        long lastMessageId = 10;
        MessageRange messageRange = MessageRange.range(firstMessageId, lastMessageId);
        TestId mailboxId = TestId.of(12);
        expect(mailbox.getMailboxId()).andReturn(mailboxId).times(10);
        
        LongStream.rangeClosed(firstMessageId, lastMessageId)
            .forEach(messageId -> {
                DeleteResponse expectedDeleteResponse = control.createMock(DeleteResponse.class);
                expect(indexer.deleteMessage(mailboxId.serialize() + ":" + messageId))
                    .andReturn(expectedDeleteResponse);
            });
        
        control.replay();
        testee.delete(session, mailbox, messageRange);
        control.verify();
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void deleteShouldNotPropagateExceptionWhenExceptionOccurs() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);
        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        long messageId = 1;
        MessageRange messageRange = MessageRange.one(messageId);
        TestId mailboxId = TestId.of(12);
        expect(mailbox.getMailboxId()).andReturn(mailboxId);
        
        expect(indexer.deleteMessage(mailboxId.serialize() + ":" + messageId))
            .andThrow(new ElasticsearchException(""));
        
        control.replay();
        testee.delete(session, mailbox, messageRange);
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateShouldWork() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);

        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        Flags flags = new Flags();

        long messageId = 1;
        TestId mailboxId = TestId.of(12);
        MessageRange messageRange = MessageRange.one(messageId);

        expectLastCall();
        expect(mailbox.getMailboxId()).andReturn(mailboxId);
        
        UpdateResponse expectedUpdateResponse = control.createMock(UpdateResponse.class);
        expect(indexer.updateMessage(eq(mailboxId.serialize() + ":" + messageId), anyString()))
            .andReturn(expectedUpdateResponse);
        
        control.replay();
        testee.update(session, mailbox, messageRange, flags, MODSEQ);
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateShouldWorkWhenMultipleMessageIds() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);

        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        Flags flags = new Flags();

        long firstMessageId = 1;
        long lastMessageId = 10;
        MessageRange messageRange = MessageRange.range(firstMessageId, lastMessageId);
        
        TestId mailboxId = TestId.of(12);

        IntStream.range(1, 11).forEach(
            (uid) -> {
                try {

                    expectLastCall();

                    expect(mailbox.getMailboxId()).andReturn(mailboxId);

                    UpdateResponse expectedUpdateResponse = control.createMock(UpdateResponse.class);
                    expect(indexer.updateMessage(eq(mailboxId.serialize() + ":" + uid), anyString()))

                        .andReturn(expectedUpdateResponse);
                } catch (Exception e) {
                    Throwables.propagate(e);
                }
            }
        );
        
        
        control.replay();
        testee.update(session, mailbox, messageRange, flags, MODSEQ);
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateShouldNotPropagateExceptionWhenExceptionOccurs() throws Exception {
        MailboxSession session = control.createMock(MailboxSession.class);

        Mailbox<TestId> mailbox = control.createMock(Mailbox.class);
        Flags flags = new Flags();

        long messageId = 1;
        TestId mailboxId = TestId.of(12);
        MessageRange messageRange = MessageRange.one(messageId);

        expectLastCall();
        expect(mailbox.getMailboxId()).andReturn(mailboxId);

        expect(indexer.updateMessage(eq(mailboxId.serialize() + ":" + messageId), anyString()))
            .andThrow(new ElasticsearchException(""));
        
        control.replay();
        testee.update(session, mailbox, messageRange, flags, MODSEQ);
        control.verify();
    }
}
