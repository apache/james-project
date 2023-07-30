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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

import com.google.common.collect.ImmutableList;

public class JPAMapperProvider implements MapperProvider {

    private final JpaTestCluster jpaTestCluster;

    public JPAMapperProvider(JpaTestCluster jpaTestCluster) {
        this.jpaTestCluster = jpaTestCluster;
    }

    @Override
    public MailboxMapper createMailboxMapper() {
        return new TransactionalMailboxMapper(new JPAMailboxMapper(jpaTestCluster.getEntityManagerFactory()));
    }

    @Override
    public MessageMapper createMessageMapper() {
        EntityManagerFactory entityManagerFactory = jpaTestCluster.getEntityManagerFactory();

        JPAMessageMapper messageMapper = new JPAMessageMapper(new JPAUidProvider(entityManagerFactory),
            new JPAModSeqProvider(entityManagerFactory),
            entityManagerFactory);

        return new TransactionalMessageMapper(messageMapper);
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return new TransactionalAttachmentMapper(new JPAAttachmentMapper(jpaTestCluster.getEntityManagerFactory()));
    }

    @Override
    public MailboxId generateId() {
        return JPAId.of(Math.abs(ThreadLocalRandom.current().nextInt()));
    }

    @Override
    public MessageId generateMessageId() {
        return new DefaultMessageId.Factory().generate();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.of(Capabilities.ANNOTATION, Capabilities.MAILBOX, Capabilities.MESSAGE, Capabilities.MOVE, Capabilities.ATTACHMENT);
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public MessageUid generateMessageUid() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public ModSeq generateModSeq(Mailbox mailbox) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

}
