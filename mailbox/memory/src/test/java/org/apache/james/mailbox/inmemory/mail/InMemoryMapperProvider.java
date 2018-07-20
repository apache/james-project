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

package org.apache.james.mailbox.inmemory.mail;

import java.util.List;
import java.util.Random;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;

import com.google.common.collect.ImmutableList;

public class InMemoryMapperProvider implements MapperProvider {

    private static final MockMailboxSession MAILBOX_SESSION = new MockMailboxSession("user");

    private final Random random;
    private final MessageId.Factory messageIdFactory;
    private final MessageUidProvider messageUidProvider;
    private final InMemoryMailboxSessionMapperFactory inMemoryMailboxSessionMapperFactory;


    public InMemoryMapperProvider() {
        random = new Random();
        messageIdFactory = new InMemoryMessageId.Factory();
        messageUidProvider = new MessageUidProvider();
        inMemoryMailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
    }

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMailboxMapper(MAILBOX_SESSION);
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMessageMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        return new InMemoryMessageIdMapper(
            inMemoryMailboxSessionMapperFactory.createMailboxMapper(MAILBOX_SESSION),
            inMemoryMailboxSessionMapperFactory.createMessageMapper(MAILBOX_SESSION));
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createAttachmentMapper(MAILBOX_SESSION);
    }

    @Override
    public InMemoryId generateId() {
        return InMemoryId.of(random.nextInt());
    }

    @Override
    public MessageUid generateMessageUid() {
        return messageUidProvider.next();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createAnnotationMapper(MAILBOX_SESSION);
    }
    
    @Override
    public MessageId generateMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.of(
            Capabilities.MESSAGE,
            Capabilities.MAILBOX,
            Capabilities.ATTACHMENT,
            Capabilities.ANNOTATION,
            Capabilities.MOVE,
            Capabilities.ACL_STORAGE,
            Capabilities.UNIQUE_MESSAGE_ID);
    }

    @Override
    public long generateModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider()
                .nextModSeq(MAILBOX_SESSION, mailbox);
    }

    @Override
    public long highestModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider()
            .highestModSeq(MAILBOX_SESSION, mailbox);
    }

}
