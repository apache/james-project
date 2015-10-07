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
package org.apache.james.mailbox.hbase;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.james.mailbox.MailboxSession;
import static org.apache.james.mailbox.hbase.HBaseNames.*;
import org.apache.james.mailbox.hbase.mail.HBaseModSeqProvider;
import org.apache.james.mailbox.hbase.mail.HBaseUidProvider;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MailboxSessionMapperFactory test.
 *
 */
public class HBaseMailboxSessionMapperFactoryTest {

    private final static Logger LOG = LoggerFactory.getLogger(HBaseMailboxSessionMapperFactoryTest.class);
    private static final HBaseClusterSingleton CLUSTER = HBaseClusterSingleton.build();
    private static Configuration conf;

    @Before
    public void beforeMethod() throws IOException {
        ensureTables();
        clearTables();
        conf = CLUSTER.getConf();
    }

    private void ensureTables() throws IOException {
        CLUSTER.ensureTable(MAILBOXES_TABLE, new byte[][]{MAILBOX_CF});
        CLUSTER.ensureTable(MESSAGES_TABLE,
                new byte[][]{MESSAGES_META_CF, MESSAGE_DATA_HEADERS_CF, MESSAGE_DATA_BODY_CF});
        CLUSTER.ensureTable(SUBSCRIPTIONS_TABLE, new byte[][]{SUBSCRIPTION_CF});
    }

    private void clearTables() {
        CLUSTER.clearTable(MAILBOXES);
        CLUSTER.clearTable(MESSAGES);
        CLUSTER.clearTable(SUBSCRIPTIONS);
    }

    /**
     * Test of createMessageMapper method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateMessageMapper() throws Exception {
        LOG.info("createMessageMapper");
        MailboxSession session = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null);
        MessageMapper<HBaseId> messageMapper = instance.createMessageMapper(session);
        assertNotNull(messageMapper);
        assertTrue(messageMapper instanceof MessageMapper);
    }

    /**
     * Test of createMailboxMapper method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateMailboxMapper() throws Exception {
        LOG.info("createMailboxMapper");
        MailboxSession session = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null);
        MailboxMapper<HBaseId> mailboxMapper = instance.createMailboxMapper(session);
        assertNotNull(mailboxMapper);
        assertTrue(mailboxMapper instanceof MailboxMapper);
    }

    /**
     * Test of createSubscriptionMapper method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateSubscriptionMapper() throws Exception {
        LOG.info("createSubscriptionMapper");
        MailboxSession session = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null);
        SubscriptionMapper subscriptionMapper = instance.createSubscriptionMapper(session);
        assertNotNull(subscriptionMapper);
        assertTrue(subscriptionMapper instanceof SubscriptionMapper);
    }

    /**
     * Test of getModSeqProvider method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testGetModSeqProvider() {
        LOG.info("getModSeqProvider");
        ModSeqProvider<HBaseId> expResult = new HBaseModSeqProvider(conf);
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, expResult);
        ModSeqProvider<HBaseId> result = instance.getModSeqProvider();
        assertEquals(expResult, result);
    }

    /**
     * Test of getUidProvider method, of class HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testGetUidProvider() {
        LOG.info("getUidProvider");
        UidProvider<HBaseId> expResult = new HBaseUidProvider(conf);
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, expResult, null);
        UidProvider<HBaseId> result = instance.getUidProvider();
        assertEquals(expResult, result);
    }
}
