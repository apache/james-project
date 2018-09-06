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

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.hbase.mail.HBaseModSeqProvider;
import org.apache.james.mailbox.hbase.mail.HBaseUidProvider;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MailboxSessionMapperFactory test.
 *
 */
public class HBaseMailboxSessionMapperFactoryTest {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseMailboxSessionMapperFactoryTest.class);
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
        MessageId.Factory messageIdFactory = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null, messageIdFactory);
        MessageMapper messageMapper = instance.createMessageMapper(session);
        assertThat(messageMapper).isNotNull();
        assertThat(messageMapper).isInstanceOf(MessageMapper.class);
    }

    /**
     * Test of createMailboxMapper method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateMailboxMapper() throws Exception {
        LOG.info("createMailboxMapper");
        MailboxSession session = null;
        MessageId.Factory messageIdFactory = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null, messageIdFactory);
        MailboxMapper mailboxMapper = instance.createMailboxMapper(session);
        assertThat(mailboxMapper).isNotNull();
        assertThat(mailboxMapper).isInstanceOf(MailboxMapper.class);
    }

    /**
     * Test of createSubscriptionMapper method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testCreateSubscriptionMapper() throws Exception {
        LOG.info("createSubscriptionMapper");
        MailboxSession session = null;
        MessageId.Factory messageIdFactory = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, null, messageIdFactory);
        SubscriptionMapper subscriptionMapper = instance.createSubscriptionMapper(session);
        assertThat(subscriptionMapper).isNotNull();
        assertThat(subscriptionMapper).isInstanceOf(SubscriptionMapper.class);
    }

    /**
     * Test of getModSeqProvider method, of class
     * HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testGetModSeqProvider() {
        LOG.info("getModSeqProvider");
        ModSeqProvider expResult = new HBaseModSeqProvider(conf);
        MessageId.Factory messageIdFactory = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, null, expResult, messageIdFactory);
        ModSeqProvider result = instance.getModSeqProvider();
        assertEquals(expResult, result);
    }

    /**
     * Test of getUidProvider method, of class HBaseMailboxSessionMapperFactory.
     */
    @Test
    public void testGetUidProvider() {
        LOG.info("getUidProvider");
        UidProvider expResult = new HBaseUidProvider(conf);
        MessageId.Factory messageIdFactory = null;
        HBaseMailboxSessionMapperFactory instance = new HBaseMailboxSessionMapperFactory(conf, expResult, null, messageIdFactory);
        UidProvider result = instance.getUidProvider();
        assertEquals(expResult, result);
    }
}
