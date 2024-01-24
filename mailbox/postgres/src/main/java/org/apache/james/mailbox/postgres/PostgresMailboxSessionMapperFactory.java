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
package org.apache.james.mailbox.postgres;

import java.time.Clock;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.postgres.mail.PostgresAnnotationMapper;
import org.apache.james.mailbox.postgres.mail.PostgresAttachmentMapper;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxMapper;
import org.apache.james.mailbox.postgres.mail.PostgresMessageIdMapper;
import org.apache.james.mailbox.postgres.mail.PostgresMessageMapper;
import org.apache.james.mailbox.postgres.mail.PostgresModSeqProvider;
import org.apache.james.mailbox.postgres.mail.PostgresUidProvider;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxAnnotationDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionDAO;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

import com.google.common.collect.ImmutableSet;

public class PostgresMailboxSessionMapperFactory extends MailboxSessionMapperFactory implements AttachmentMapperFactory {

    private final PostgresExecutor.Factory executorFactory;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final Clock clock;

    @Inject
    public PostgresMailboxSessionMapperFactory(PostgresExecutor.Factory executorFactory,
                                               Clock clock,
                                               BlobStore blobStore,
                                               BlobId.Factory blobIdFactory) {
        this.executorFactory = executorFactory;
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;
        this.clock = clock;
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) {
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(executorFactory.create(session.getUser().getDomainPart()));
        return new PostgresMailboxMapper(mailboxDAO);
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) {
        return new PostgresMessageMapper(executorFactory.create(session.getUser().getDomainPart()),
            getModSeqProvider(session),
            getUidProvider(session),
            blobStore,
            clock,
            blobIdFactory);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) {
        return new PostgresMessageIdMapper(new PostgresMailboxDAO(executorFactory.create(session.getUser().getDomainPart())),
            new PostgresMessageDAO(executorFactory.create(session.getUser().getDomainPart()), blobIdFactory),
            new PostgresMailboxMessageDAO(executorFactory.create(session.getUser().getDomainPart())),
            getModSeqProvider(session),
            blobStore,
            blobIdFactory,
            clock);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) {
        return new PostgresSubscriptionMapper(new PostgresSubscriptionDAO(executorFactory.create(session.getUser().getDomainPart())));
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession session) {
        return new PostgresAnnotationMapper(new PostgresMailboxAnnotationDAO(executorFactory.create(session.getUser().getDomainPart())));
    }

    @Override
    public PostgresUidProvider getUidProvider(MailboxSession session) {
        return new PostgresUidProvider.Factory(executorFactory).create(session);
    }

    @Override
    public PostgresModSeqProvider getModSeqProvider(MailboxSession session) {
        return new PostgresModSeqProvider.Factory(executorFactory).create(session);
    }

    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession session) {
        PostgresAttachmentDAO postgresAttachmentDAO = new PostgresAttachmentDAO(executorFactory.create(session.getUser().getDomainPart()), blobIdFactory);
        return new PostgresAttachmentMapper(postgresAttachmentDAO, blobStore);
    }

    @Override
    public AttachmentMapper getAttachmentMapper(MailboxSession session) {
        return createAttachmentMapper(session);
    }

    protected DeleteMessageListener deleteMessageListener() {
        PostgresMessageDAO.Factory postgresMessageDAOFactory = new PostgresMessageDAO.Factory(blobIdFactory, executorFactory);
        PostgresMailboxMessageDAO.Factory postgresMailboxMessageDAOFactory = new PostgresMailboxMessageDAO.Factory(executorFactory);
        PostgresAttachmentDAO.Factory attachmentDAOFactory = new PostgresAttachmentDAO.Factory(executorFactory, blobIdFactory);
        PostgresMailboxFactory postgresMailboxFactory = new PostgresMailboxFactory(executorFactory);

        return new DeleteMessageListener(blobStore, postgresMailboxMessageDAOFactory, postgresMessageDAOFactory,
            attachmentDAOFactory, postgresMailboxFactory, ImmutableSet.of());
    }
}
