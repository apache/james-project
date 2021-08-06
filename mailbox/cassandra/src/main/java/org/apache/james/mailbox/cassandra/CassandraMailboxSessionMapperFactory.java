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

package org.apache.james.mailbox.cassandra;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.ACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraAnnotationMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraIndexTableHandler;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.user.CassandraSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

import com.datastax.driver.core.Session;

/**
 * Cassandra implementation of {@link MailboxSessionMapperFactory}
 */
public class CassandraMailboxSessionMapperFactory extends MailboxSessionMapperFactory implements AttachmentMapperFactory {
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;
    private final CassandraThreadDAO threadDAO;
    private final CassandraThreadLookupDAO threadLookupDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final CassandraDeletedMessageDAO deletedMessageDAO;
    private final BlobStore blobStore;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;
    private final ACLMapper aclMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraConfiguration cassandraConfiguration;
    private final CassandraMailboxMapper cassandraMailboxMapper;
    private final CassandraSubscriptionMapper cassandraSubscriptionMapper;
    private final CassandraAttachmentMapper cassandraAttachmentMapper;
    private final CassandraMessageMapper cassandraMessageMapper;
    private final CassandraMessageIdMapper cassandraMessageIdMapper;
    private final CassandraAnnotationMapper cassandraAnnotationMapper;

    @Inject
    public CassandraMailboxSessionMapperFactory(UidProvider uidProvider, ModSeqProvider modSeqProvider, Session session,
                                                CassandraThreadDAO threadDAO, CassandraThreadLookupDAO threadLookupDAO,
                                                CassandraMessageDAO messageDAO,
                                                CassandraMessageDAOV3 messageDAOV3, CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO,
                                                CassandraMailboxCounterDAO mailboxCounterDAO, CassandraMailboxRecentsDAO mailboxRecentsDAO, CassandraMailboxDAO mailboxDAO,
                                                CassandraMailboxPathV3DAO mailboxPathV3DAO, CassandraFirstUnseenDAO firstUnseenDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                                CassandraAttachmentDAOV2 attachmentDAOV2, CassandraDeletedMessageDAO deletedMessageDAO,
                                                BlobStore blobStore, CassandraAttachmentMessageIdDAO attachmentMessageIdDAO,
                                                CassandraAttachmentOwnerDAO ownerDAO, ACLMapper aclMapper,
                                                CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                                CassandraSchemaVersionManager versionManager,
                                                RecomputeMailboxCountersService recomputeMailboxCountersService,
                                                CassandraUtils cassandraUtils, CassandraConfiguration cassandraConfiguration) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.threadDAO = threadDAO;
        this.threadLookupDAO = threadLookupDAO;
        this.messageDAO = messageDAO;
        this.messageDAOV3 = messageDAOV3;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentsDAO = mailboxRecentsDAO;
        this.firstUnseenDAO = firstUnseenDAO;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.deletedMessageDAO = deletedMessageDAO;
        this.applicableFlagDAO = applicableFlagDAO;
        this.blobStore = blobStore;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.aclMapper = aclMapper;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.ownerDAO = ownerDAO;
        this.cassandraConfiguration = cassandraConfiguration;
        CassandraIndexTableHandler indexTableHandler = new CassandraIndexTableHandler(
            mailboxRecentsDAO,
            mailboxCounterDAO,
            firstUnseenDAO,
            applicableFlagDAO,
            deletedMessageDAO);
        this.cassandraMailboxMapper = new CassandraMailboxMapper(mailboxDAO, mailboxPathV3DAO, userMailboxRightsDAO, aclMapper, cassandraConfiguration);
        this.cassandraSubscriptionMapper = new CassandraSubscriptionMapper(session, cassandraUtils);
        this.cassandraAttachmentMapper = new CassandraAttachmentMapper(attachmentDAOV2, blobStore, attachmentMessageIdDAO, ownerDAO);
        this.cassandraMessageMapper = new CassandraMessageMapper(
            uidProvider,
            modSeqProvider,
            cassandraAttachmentMapper,
            messageDAO,
            messageDAOV3,
            messageIdDAO,
            imapUidDAO,
            mailboxCounterDAO,
            mailboxRecentsDAO,
            applicableFlagDAO,
            indexTableHandler,
            firstUnseenDAO,
            deletedMessageDAO,
            blobStore,
            cassandraConfiguration, recomputeMailboxCountersService);
        this.cassandraMessageIdMapper = new CassandraMessageIdMapper(cassandraMailboxMapper, mailboxDAO,
            cassandraAttachmentMapper, imapUidDAO, messageIdDAO, messageDAO, messageDAOV3, indexTableHandler,
            modSeqProvider, blobStore, cassandraConfiguration);
        this.cassandraAnnotationMapper = new CassandraAnnotationMapper(session, cassandraUtils);
    }

    @Override
    public CassandraMessageMapper createMessageMapper(MailboxSession mailboxSession) {
        return cassandraMessageMapper;
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession mailboxSession) {
        return cassandraMessageIdMapper;
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession mailboxSession) {
        return cassandraMailboxMapper;
    }

    @Override
    public CassandraAttachmentMapper createAttachmentMapper(MailboxSession mailboxSession) {
        return cassandraAttachmentMapper;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession mailboxSession) {
        return cassandraSubscriptionMapper;
    }

    @Override
    public ModSeqProvider getModSeqProvider() {
        return modSeqProvider;
    }

    @Override
    public UidProvider getUidProvider() {
        return uidProvider;
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession mailboxSession) {
        return cassandraAnnotationMapper;
    }

    @Override
    public AttachmentMapper getAttachmentMapper(MailboxSession session) {
        return cassandraAttachmentMapper;
    }

    public DeleteMessageListener deleteMessageListener() {
        return new DeleteMessageListener(threadDAO, threadLookupDAO, imapUidDAO, messageIdDAO, messageDAO, messageDAOV3, attachmentDAOV2, ownerDAO,
            attachmentMessageIdDAO, aclMapper, userMailboxRightsDAO, applicableFlagDAO, firstUnseenDAO, deletedMessageDAO,
            mailboxCounterDAO, mailboxRecentsDAO, blobStore, cassandraConfiguration);
    }
}
