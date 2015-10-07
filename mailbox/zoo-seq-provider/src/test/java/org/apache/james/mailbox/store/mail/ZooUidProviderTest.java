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
package org.apache.james.mailbox.store.mail;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.netflix.curator.RetryPolicy;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryOneTime;
import com.netflix.curator.test.TestingServer;

/**
 * Test for UID provider.
 */
public class ZooUidProviderTest {

    public static class LongId implements MailboxId {

        public final Long id;

        public LongId(long id) {
            this.id = id;
        }

        @Override
        public String serialize() {
            return String.valueOf(id);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LongId other = (LongId) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            return true;
        }

    }

    public static class UUIDId implements MailboxId {

        private final UUID id;

        public static UUIDId of(UUID id) {
            return new UUIDId(id);
        }

        public UUIDId(UUID id) {
            this.id = id;
        }

        @Override
        public String serialize() {
            return id.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            UUIDId other = (UUIDId) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            return true;
        }

    }
	
		
	    private static TestingServer testServer;
	    private static final int ZOO_TEST_PORT = 3123;
	    private final RetryPolicy retryPolicy = new RetryOneTime(1);
	    private CuratorFramework client;
	    private ZooUidProvider<UUIDId> uuidProvider;
	    private ZooUidProvider<LongId> longProvider;
	    private SimpleMailbox<UUIDId> mailboxUUID;
	    private SimpleMailbox<LongId> mailboxLong;
	    private UUID randomUUID = UUID.randomUUID();
	
	    @Before
	    public void setUp() throws Exception {
	        testServer = new TestingServer(ZOO_TEST_PORT);
	        client = CuratorFrameworkFactory.builder().connectString("localhost:" + ZOO_TEST_PORT).retryPolicy(retryPolicy).
	                namespace("JAMES").build();
	        client.start();
	        uuidProvider = new ZooUidProvider<UUIDId>(client, retryPolicy);
	        longProvider = new ZooUidProvider<LongId>(client, retryPolicy);
	        MailboxPath path1 = new MailboxPath("namespacetest", "namespaceuser", "UUID");
	        MailboxPath path2 = new MailboxPath("namespacetest", "namespaceuser", "Long");
	        mailboxUUID = new SimpleMailbox<UUIDId>(path1, 1L);
	        mailboxUUID.setMailboxId(UUIDId.of(randomUUID));
	        mailboxLong = new SimpleMailbox<LongId>(path2, 2L);
	        mailboxLong.setMailboxId(new LongId(123L));
	    }
	
	    @After
	    public void tearDown() throws Exception {
	        client.close();
	        testServer.close();
	    }
	
	    /**
	     * Test of nextUid method, of class ZooUidProvider.
	     */
	    @Test
	    public void testNextUid() throws Exception {
	        System.out.println("Testing nextUid");
	        long result = uuidProvider.nextUid(null, mailboxUUID);
	        assertEquals("Next UID is 1", 1, result);
	        result = longProvider.nextUid(null, mailboxLong);
	        assertEquals("Next UID is 1", 1, result);
	    }
	
	    /**
	     * Test of lastUid method, of class ZooUidProvider.
	     */
	    @Test
	    public void testLastUid() throws Exception {
	        System.out.println("Testing lastUid");
	        long result = uuidProvider.lastUid(null, mailboxUUID);
	        assertEquals("Next UID is 0", 0, result);
	        result = uuidProvider.nextUid(null, mailboxUUID);
	        assertEquals("Next UID is 1", 1, result);
	    }
	
	    /**
	     * Test of lastUid method, of class ZooUidProvider.
	     */
	    @Test
	    public void testLongLastUid() throws Exception {
	        System.out.println("Testing long lastUid");
	        long result = longProvider.lastUid(null, mailboxLong);
	        assertEquals("Next UID is 0", 0, result);
	        result = longProvider.nextUid(null, mailboxLong);
	        assertEquals("Next UID is 1", 1, result);
	    }
	}
