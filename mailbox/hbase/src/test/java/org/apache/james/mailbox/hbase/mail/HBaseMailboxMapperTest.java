/**
 * **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one * or more
 * contributor license agreements. See the NOTICE file * distributed with this
 * work for additional information * regarding copyright ownership. The ASF
 * licenses this file * to you under the Apache License, Version 2.0 (the *
 * "License"); you may not use this file except in compliance * with the
 * License. You may obtain a copy of the License at * *
 * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
 * law or agreed to in writing, * software distributed under the License is
 * distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY *
 * KIND, either express or implied. See the License for the * specific language
 * governing permissions and limitations * under the License. *
 * **************************************************************
 */
package org.apache.james.mailbox.hbase.mail;

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
import static org.apache.james.mailbox.hbase.HBaseUtils.mailboxFromResult;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.io.ChunkInputStream;
import org.apache.james.mailbox.hbase.io.ChunkOutputStream;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HBaseMailboxMapper unit tests.
 *
 */
public class HBaseMailboxMapperTest {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseMailboxMapperTest.class);
    public static final HBaseClusterSingleton CLUSTER = HBaseClusterSingleton.build();
    private static Configuration conf;
    private static HBaseMailboxMapper mapper;
    private static List<HBaseMailbox> mailboxList;
    private static List<MailboxPath> pathsList;
    private static final int NAMESPACES = 5;
    private static final int USERS = 5;
    private static final int MAILBOX_NO = 5;
    private static final char SEPARATOR = '%';

    @Before
    public void setUp() throws Exception {
        ensureTables();
        // start the test cluster
        clearTables();
        conf = CLUSTER.getConf();
        fillMailboxList();
        mapper = new HBaseMailboxMapper(conf);
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

    /**
     * Test an ordered scenario with list, delete... methods.
     *
     * @throws Exception
     */
    @Test
    public void testMailboxMapperScenario() throws Exception {
        testFindMailboxByPath();
        testFindMailboxWithPathLike();
        testList();
        testSave();
        testDelete();
        testHasChildren();
//        testDeleteAllMemberships(); // Ignore this test
        testDeleteAllMailboxes();
        testChunkStream();
    }

    /**
     * Test of findMailboxByPath method, of class HBaseMailboxMapper.
     */
    private void testFindMailboxByPath() throws Exception {
        LOG.info("findMailboxByPath");
        HBaseMailbox mailbox;
        for (MailboxPath path : pathsList) {
            LOG.info("Searching for " + path);
            mailbox = (HBaseMailbox) mapper.findMailboxByPath(path);
            assertEquals(path, new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()));
        }
    }

    /**
     * Test of findMailboxWithPathLike method, of class HBaseMailboxMapper.
     */
    private void testFindMailboxWithPathLike() throws Exception {
        LOG.info("findMailboxWithPathLike");
        MailboxPath path = pathsList.get(pathsList.size() / 2);

        List<Mailbox<HBaseId>> result = mapper.findMailboxWithPathLike(path);
        assertEquals(1, result.size());

        int start = 3;
        int end = 7;
        MailboxPath newPath;

        for (int i = start; i < end; i++) {
            newPath = new MailboxPath(path);
            newPath.setName(i + newPath.getName() + " " + i);
            // test for paths with null user
            if (i % 2 == 0) {
                newPath.setUser(null);
            }
            addMailbox(new HBaseMailbox(newPath, 1234));
        }
        result = mapper.findMailboxWithPathLike(path);
        assertEquals(end - start + 1, result.size());
    }

    /**
     * Test of list method, of class HBaseMailboxMapper.
     */
    private void testList() throws Exception {
        LOG.info("list");
        List<Mailbox<HBaseId>> result = mapper.list();
        assertEquals(mailboxList.size(), result.size());

    }

    /**
     * Test of save method, of class HBaseMailboxMapper.
     */
    private void testSave() throws Exception {
        LOG.info("save and mailboxFromResult");
        final HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        try {
            
            final HBaseMailbox mlbx = mailboxList.get(mailboxList.size() / 2);
        
            final Get get = new Get(mlbx.getMailboxId().toBytes());
            // get all columns for the DATA column family
            get.addFamily(MAILBOX_CF);
        
            final Result result = mailboxes.get(get);
            final HBaseMailbox newValue = (HBaseMailbox) mailboxFromResult(result);
            assertEquals(mlbx, newValue);
            assertEquals(mlbx.getUser(), newValue.getUser());
            assertEquals(mlbx.getName(), newValue.getName());
            assertEquals(mlbx.getNamespace(), newValue.getNamespace());
            assertEquals(mlbx.getMailboxId(), newValue.getMailboxId());
            assertEquals(mlbx.getLastUid(), newValue.getLastUid());
            assertEquals(mlbx.getUidValidity(), newValue.getUidValidity());
            assertEquals(mlbx.getHighestModSeq(), newValue.getHighestModSeq());
            assertArrayEquals(mlbx.getMailboxId().toBytes(), newValue.getMailboxId().toBytes());
        } finally {
            mailboxes.close();
        }
    }

    /**
     * Test of delete method, of class HBaseMailboxMapper.
     */
    private void testDelete() throws Exception {
        LOG.info("delete");
        // delete last 5 mailboxes from mailboxList
        int offset = 5;
        int notFoundCount = 0;

        Iterator<HBaseMailbox> iterator = mailboxList.subList(mailboxList.size() - offset, mailboxList.size()).iterator();

        while (iterator.hasNext()) {
            HBaseMailbox mailbox = iterator.next();
            mapper.delete(mailbox);
            iterator.remove();
            MailboxPath path = new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName());
            pathsList.remove(path);
            LOG.info("Removing mailbox: {}", path);
            try {
                mapper.findMailboxByPath(path);
            } catch (MailboxNotFoundException e) {
                LOG.info("Succesfully removed {}", mailbox);
                notFoundCount++;
            }
        }
        assertEquals(offset, notFoundCount);
        assertEquals(mailboxList.size(), mapper.list().size());
    }

    /**
     * Test of hasChildren method, of class HBaseMailboxMapper.
     */
    private void testHasChildren() throws Exception {
        LOG.info("hasChildren");
        String oldName;
        for (MailboxPath path : pathsList) {
            final HBaseMailbox mailbox = new HBaseMailbox(path, 12455);
            oldName = mailbox.getName();
            if (path.getUser().equals("user3")) {
                mailbox.setName("test");
            }
            boolean result = mapper.hasChildren(mailbox, SEPARATOR);
            mailbox.setName(oldName);
            if (path.getUser().equals("user3")) {
                assertTrue(result);
            } else {
                assertFalse(result);
            }

        }
    }

    /**
     * Test of deleteAllMailboxes method, of class HBaseMailboxMapper.
     */
    private void testDeleteAllMailboxes() throws MailboxException {
        LOG.info("deleteAllMailboxes");
        mapper.deleteAllMailboxes();
        assertEquals(0, mapper.list().size());
        fillMailboxList();
    }

    private void testChunkStream() throws IOException {
        LOG.info("Checking ChunkOutpuStream and ChunkInputStream");
        final String original = "This is a proper test for the HBase ChunkInputStream and"
                + "ChunkOutputStream. This text must be larger than the chunk size so we write"
                + "and read more then one chunk size. I think that a few more lore ipsum lines"
                + "will be enough."
                + "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor "
                + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis "
                + "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
                + "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu "
                + "fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa"
                + " qui officia deserunt mollit anim id est laborum";
        byte[] data = Bytes.toBytes(original);
        // we make the column size = 10 bytes
        ChunkOutputStream out = new ChunkOutputStream(conf,
                MESSAGES_TABLE, MESSAGE_DATA_BODY_CF, Bytes.toBytes("10"), 10);
        ChunkInputStream in = new ChunkInputStream(conf,
                MESSAGES_TABLE, MESSAGE_DATA_BODY_CF, Bytes.toBytes("10"));
        try {
            //create the stream
            ByteArrayInputStream bin = new ByteArrayInputStream(data);
            ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length);
            int b;
            while ((b = bin.read()) != -1) {
                out.write(b);
            }
            out.close();
            while ((b = in.read()) != -1) {
                bout.write(b);
            }
            String s = bout.toString();
            assertTrue(original.equals(s));
        } finally {
            in.close();
            out.close();
        }
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
        LOG.info("Created test case with {} mailboxes and {} paths",
                mailboxList.size(), pathsList.size());
    }

    private void addMailbox(HBaseMailbox mailbox) throws MailboxException {
        mailboxList.add(mailbox);
        pathsList.add(new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()));
        mapper = new HBaseMailboxMapper(conf);
        mapper.save(mailbox);
        LOG.info("Added new mailbox: {} paths: {}", mailboxList.size(), pathsList.size());
    }
}
