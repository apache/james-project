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
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
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
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
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
    protected static final String ATTACHMENTMAPPER = "ATTACHMENTMAPPER";

    private final Session session;
    private final CassandraUidProvider uidProvider;
    private final CassandraModSeqProvider modSeqProvider;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathDAOImpl mailboxPathDAO;
    private final CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final CassandraDeletedMessageDAO deletedMessageDAO;
    private final BlobStore blobStore;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;
    private final CassandraACLMapper aclMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraSchemaVersionManager versionManager;
    private final CassandraUtils cassandraUtils;
    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public CassandraMailboxSessionMapperFactory(CassandraUidProvider uidProvider, CassandraModSeqProvider modSeqProvider, Session session,
                                                CassandraMessageDAO messageDAO,
                                                CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO,
                                                CassandraMailboxCounterDAO mailboxCounterDAO, CassandraMailboxRecentsDAO mailboxRecentsDAO, CassandraMailboxDAO mailboxDAO,
                                                CassandraMailboxPathDAOImpl mailboxPathDAO, CassandraMailboxPathV2DAO mailboxPathV2DAO, CassandraFirstUnseenDAO firstUnseenDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                                CassandraAttachmentDAOV2 attachmentDAOV2, CassandraDeletedMessageDAO deletedMessageDAO,
                                                BlobStore blobStore, CassandraAttachmentMessageIdDAO attachmentMessageIdDAO,
                                                CassandraAttachmentOwnerDAO ownerDAO, CassandraACLMapper aclMapper,
                                                CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                                CassandraSchemaVersionManager versionManager,
                                                CassandraUtils cassandraUtils, CassandraConfiguration cassandraConfiguration) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.session = session;
        this.messageDAO = messageDAO;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentsDAO = mailboxRecentsDAO;
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.mailboxPathV2DAO = mailboxPathV2DAO;
        this.firstUnseenDAO = firstUnseenDAO;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.deletedMessageDAO = deletedMessageDAO;
        this.applicableFlagDAO = applicableFlagDAO;
        this.blobStore = blobStore;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.aclMapper = aclMapper;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.versionManager = versionManager;
        this.cassandraUtils = cassandraUtils;
        this.ownerDAO = ownerDAO;
        this.cassandraConfiguration = cassandraConfiguration;
        this.indexTableHandler = new CassandraIndexTableHandler(
            mailboxRecentsDAO,
            mailboxCounterDAO,
            firstUnseenDAO,
            applicableFlagDAO,
            deletedMessageDAO);
    }

    @Override
    public CassandraMessageMapper createMessageMapper(MailboxSession mailboxSession) {
        return new CassandraMessageMapper(
                                          uidProvider,
                                          modSeqProvider,
                                          createAttachmentMapper(mailboxSession),
                                          messageDAO,
                                          messageIdDAO,
                                          imapUidDAO,
                                          mailboxCounterDAO,
                                          mailboxRecentsDAO,
                                          applicableFlagDAO,
                                          indexTableHandler,
                                          firstUnseenDAO,
                                          deletedMessageDAO,
                                          cassandraConfiguration);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession mailboxSession) {
        return new CassandraMessageIdMapper(getMailboxMapper(mailboxSession), mailboxDAO,
                createAttachmentMapper(mailboxSession),
                imapUidDAO, messageIdDAO, messageDAO, indexTableHandler, modSeqProvider,
                cassandraConfiguration);
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession mailboxSession) {
        return new CassandraMailboxMapper(mailboxDAO, mailboxPathDAO, mailboxPathV2DAO, userMailboxRightsDAO, aclMapper, versionManager);
    }

    @Override
    public CassandraAttachmentMapper createAttachmentMapper(MailboxSession mailboxSession) {
        return new CassandraAttachmentMapper(attachmentDAOV2, blobStore, attachmentMessageIdDAO, ownerDAO);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession mailboxSession) {
        return new CassandraSubscriptionMapper(session, cassandraUtils);
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
        return new CassandraAnnotationMapper(session, cassandraUtils);
    }

    @Override
    public AttachmentMapper getAttachmentMapper(MailboxSession session) {
        AttachmentMapper mapper = (AttachmentMapper) session.getAttributes().get(ATTACHMENTMAPPER);
        if (mapper == null) {
            mapper = createAttachmentMapper(session);
            session.getAttributes().put(ATTACHMENTMAPPER, mapper);
        }
        return mapper;
    }

    public DeleteMessageListener deleteMessageListener() {
        return new DeleteMessageListener(imapUidDAO, messageIdDAO, messageDAO, attachmentDAOV2, ownerDAO,
            attachmentMessageIdDAO, aclMapper, userMailboxRightsDAO, applicableFlagDAO, firstUnseenDAO, deletedMessageDAO,
            mailboxCounterDAO, mailboxRecentsDAO, blobStore);
    }
}
