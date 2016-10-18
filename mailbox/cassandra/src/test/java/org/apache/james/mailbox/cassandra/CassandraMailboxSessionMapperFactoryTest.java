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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MailboxSessionMapperFactory test.
 * 
 */
public class CassandraMailboxSessionMapperFactoryTest {
    private static final CassandraCluster CLUSTER = CassandraCluster.create(
            new CassandraModuleComposite(new CassandraModSeqModule(), new CassandraUidModule()));
    private final static Logger LOG = LoggerFactory.getLogger(CassandraMailboxSessionMapperFactoryTest.class);

    @Before
    public void beforeMethod() {
        CLUSTER.ensureAllTables();
        CLUSTER.clearAllTables();
    }

    /**
     * Test of createMessageMapper method, of class
     * CassandraMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateMessageMapper() throws Exception {
        LOG.info("createMessageMapper");
        MailboxSession session = null;
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        CassandraMailboxSessionMapperFactory instance = new CassandraMailboxSessionMapperFactory(null, null, null, null, messageIdFactory);
        MessageMapper messageMapper = instance.createMessageMapper(session);
        assertNotNull(messageMapper);
        assertTrue(messageMapper instanceof MessageMapper);
    }

    /**
     * Test of createMailboxMapper method, of class
     * CassandraMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateMailboxMapper() throws Exception {
        LOG.info("createMailboxMapper");
        MailboxSession session = null;
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        CassandraMailboxSessionMapperFactory instance = new CassandraMailboxSessionMapperFactory(null, null, null, null, messageIdFactory);
        MailboxMapper mailboxMapper = instance.createMailboxMapper(session);
        assertNotNull(mailboxMapper);
        assertTrue(mailboxMapper instanceof MailboxMapper);
    }

    /**
     * Test of createSubscriptionMapper method, of class
     * CassandraMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateSubscriptionMapper() throws Exception {
        LOG.info("createSubscriptionMapper");
        MailboxSession session = null;
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        CassandraMailboxSessionMapperFactory instance = new CassandraMailboxSessionMapperFactory(null, null, null, null, messageIdFactory);
        SubscriptionMapper subscriptionMapper = instance.createSubscriptionMapper(session);
        assertNotNull(subscriptionMapper);
        assertTrue(subscriptionMapper instanceof SubscriptionMapper);
    }

    /**
     * Test of getModSeqProvider method, of class
     * CassandraMailboxSessionMapperFactory.
     */
    @Test
    public void testGetModSeqProvider() {
        LOG.info("getModSeqProvider");
        ModSeqProvider expResult = new CassandraModSeqProvider(CLUSTER.getConf());
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        CassandraMailboxSessionMapperFactory instance = new CassandraMailboxSessionMapperFactory(null, expResult, null, null, messageIdFactory);
        ModSeqProvider result = instance.getModSeqProvider();
        assertEquals(expResult, result);
    }

    /**
     * Test of getUidProvider method, of class
     * CassandraMailboxSessionMapperFactory.
     */
    @Test
    public void testGetUidProvider() {
        LOG.info("getUidProvider");
        UidProvider expResult = new CassandraUidProvider(CLUSTER.getConf());
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        CassandraMailboxSessionMapperFactory instance = new CassandraMailboxSessionMapperFactory((CassandraUidProvider) expResult, null, null, null, messageIdFactory);
        UidProvider result = instance.getUidProvider();
        assertEquals(expResult, result);
    }
}
