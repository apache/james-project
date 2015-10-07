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
package org.apache.james.mailbox.hbase.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import static org.apache.james.mailbox.hbase.HBaseNames.*;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for UidProvider and ModSeqProvider.
 *
 */
public class HBaseUidAndModSeqProviderTest {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseUidAndModSeqProviderTest.class);
    private static final HBaseClusterSingleton CLUSTER = HBaseClusterSingleton.build();
    private static Configuration conf;
    private static HBaseUidProvider uidProvider;
    private static HBaseModSeqProvider modSeqProvider;
    private static HBaseMailboxMapper mapper;
    private static List<HBaseMailbox> mailboxList;
    private static List<MailboxPath> pathsList;
    private static final int NAMESPACES = 5;
    private static final int USERS = 5;
    private static final int MAILBOX_NO = 5;
    private static final char SEPARATOR = '%';

    @Before
    public void setUpClass() throws Exception {
        ensureTables();
        clearTables();
        conf = CLUSTER.getConf();
        uidProvider = new HBaseUidProvider(conf);
        modSeqProvider = new HBaseModSeqProvider(conf);
        mapper = new HBaseMailboxMapper(conf);
        fillMailboxList();
        for (HBaseMailbox mailbox : mailboxList) {
            mapper.save(mailbox);
        }
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

    private static void fillMailboxList() {
        mailboxList = new ArrayList<HBaseMailbox>();
        pathsList = new ArrayList<MailboxPath>();
        MailboxPath path;
        String name;
        for (int i = 0; i < NAMESPACES; i++) {
            for (int j = 0; j < USERS; j++) {
                for (int k = 0; k < MAILBOX_NO; k++) {
                    if (j == 3) {
                        name = "test" + SEPARATOR + "subbox" + k;
                    } else {
                        name = "mailbox" + k;
                    }
                    path = new MailboxPath("namespace" + i, "user" + j, name);
                    pathsList.add(path);
                    mailboxList.add(new HBaseMailbox(path, 13));
                }
            }
        }

        LOG.info("Created test case with {} mailboxes and {} paths", mailboxList.size(),
                pathsList.size());
    }

    /**
     * Test of lastUid method, of class HBaseUidProvider.
     */
    @Test
    public void testLastUid() throws Exception {
        LOG.info("lastUid");
        final MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        final HBaseMailbox newBox = new HBaseMailbox(path, 1234);
        mapper.save(newBox);
        mailboxList.add(newBox);
        pathsList.add(path);

        final long result = uidProvider.lastUid(null, newBox);
        assertEquals(0, result);
        for (int i = 1; i < 10; i++) {
            final long uid = uidProvider.nextUid(null, newBox);
            assertEquals(uid, uidProvider.lastUid(null, newBox));
        }
    }

    /**
     * Test of nextUid method, of class HBaseUidProvider.
     */
    @Test
    public void testNextUid() throws Exception {
        LOG.info("nextUid");
        final HBaseMailbox mailbox = mailboxList.get(mailboxList.size() / 2);
        final long lastUid = uidProvider.lastUid(null, mailbox);
        long result;
        for (int i = (int) lastUid + 1; i < (lastUid + 10); i++) {
            result = uidProvider.nextUid(null, mailbox);
            assertEquals(i, result);
        }
    }

    /**
     * Test of highestModSeq method, of class HBaseModSeqProvider.
     */
    @Test
    public void testHighestModSeq() throws Exception {
        LOG.info("highestModSeq");
        LOG.info("lastUid");
        MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        HBaseMailbox newBox = new HBaseMailbox(path, 1234);
        mapper.save(newBox);
        mailboxList.add(newBox);
        pathsList.add(path);

        long result = modSeqProvider.highestModSeq(null, newBox);
        assertEquals(0, result);
        for (int i = 1; i < 10; i++) {
            long uid = modSeqProvider.nextModSeq(null, newBox);
            assertEquals(uid, modSeqProvider.highestModSeq(null, newBox));
        }
    }

    /**
     * Test of nextModSeq method, of class HBaseModSeqProvider.
     */
    @Test
    public void testNextModSeq() throws Exception {
        LOG.info("nextModSeq");
        final HBaseMailbox mailbox = mailboxList.get(mailboxList.size() / 2);
        final long lastUid = modSeqProvider.highestModSeq(null, mailbox);
        long result;
        for (int i = (int) lastUid + 1; i < (lastUid + 10); i++) {
            result = modSeqProvider.nextModSeq(null, mailbox);
            assertEquals(i, result);
        }
    }
}
