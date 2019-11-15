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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.AbstractSubscriptionManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test Cassandra subscription against some general purpose written code.
 */
@ExtendWith(CassandraRestartExtension.class)
class CassandraSubscriptionManagerTest extends AbstractSubscriptionManagerTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSubscriptionModule.MODULE);

    @Override
    protected SubscriptionManager createSubscriptionManager() {
        CassandraMessageIdToImapUidDAO imapUidDAO = null;
        CassandraMessageDAO messageDAO = null;
        CassandraMessageIdDAO messageIdDAO = null;
        CassandraMailboxCounterDAO mailboxCounterDAO = null;
        CassandraMailboxRecentsDAO mailboxRecentsDAO = null;
        CassandraMailboxDAO mailboxDAO = null;
        CassandraMailboxPathDAOImpl mailboxPathDAO = null;
        CassandraMailboxPathV2DAO mailboxPathV2DAO = null;
        CassandraFirstUnseenDAO firstUnseenDAO = null;
        CassandraApplicableFlagDAO applicableFlagDAO = null;
        CassandraAttachmentDAO attachmentDAO = null;
        CassandraDeletedMessageDAO deletedMessageDAO = null;
        CassandraAttachmentDAOV2 attachmentDAOV2 = null;
        CassandraAttachmentMessageIdDAO attachmentMessageIdDAO = null;
        CassandraAttachmentOwnerDAO ownerDAO = null;
        CassandraACLMapper aclMapper = null;
        CassandraUserMailboxRightsDAO userMailboxRightsDAO = null;
        BlobStore blobStore = null;
        CassandraUidProvider uidProvider = null;
        CassandraModSeqProvider modSeqProvider = null;
        return new CassandraSubscriptionManager(
            new CassandraMailboxSessionMapperFactory(
                uidProvider,
                modSeqProvider,
                cassandraCluster.getCassandraCluster().getConf(),
                messageDAO,
                messageIdDAO,
                imapUidDAO,
                mailboxCounterDAO,
                mailboxRecentsDAO,
                mailboxDAO,
                mailboxPathDAO,
                mailboxPathV2DAO,
                firstUnseenDAO,
                applicableFlagDAO,
                attachmentDAO,
                attachmentDAOV2,
                deletedMessageDAO,
                blobStore,
                attachmentMessageIdDAO,
                ownerDAO,
                aclMapper,
                userMailboxRightsDAO,
                CassandraUtils.WITH_DEFAULT_CONFIGURATION,
                CassandraConfiguration.DEFAULT_CONFIGURATION));
    }
}
