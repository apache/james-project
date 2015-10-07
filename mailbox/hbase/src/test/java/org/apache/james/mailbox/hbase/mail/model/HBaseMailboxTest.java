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
package org.apache.james.mailbox.hbase.mail.model;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Test;

/**
 * Unit tests for HBaseMailbox class.
 */
public class HBaseMailboxTest {

    /**
     * Test of getter and setter for MailboxId
     */
    @Test
    public void testGetSetMailboxId() {
        System.out.println("getSetMailboxId");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 10);

        HBaseId expResult = HBaseId.of(UUID.randomUUID());
        instance.setMailboxId(expResult);
        assertEquals(expResult, instance.getMailboxId());

    }

    /**
     * Test of getter and setter for Namespace, of class HBaseMailbox.
     */
    @Test
    public void testGetSetNamespace() {
        System.out.println("getSetNamespace");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 124566);
        String result = instance.getNamespace();
        assertEquals(mailboxPath.getNamespace(), result);

        instance.setNamespace("newName");
        assertEquals("newName", instance.getNamespace());

    }

    /**
     * Test of getter and setter for User, of class HBaseMailbox.
     */
    @Test
    public void testGetSetUser() {
        System.out.println("getUser");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 12);
        String result = instance.getUser();
        assertEquals(mailboxPath.getUser(), result);

        instance.setUser("eric");
        assertEquals("eric", instance.getUser());
    }

    /**
     * Test of getter and setter for Name, of class HBaseMailbox.
     */
    @Test
    public void testGetSetName() {
        System.out.println("getSetName");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 1677);
        String result = instance.getName();
        assertEquals(mailboxPath.getName(), result);

        instance.setName("newINBOX");
        assertEquals("newINBOX", instance.getName());
    }

    /**
     * Test of getUidValidity method, of class HBaseMailbox.
     */
    @Test
    public void testGetUidValidity() {
        System.out.println("getUidValidity");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 123345);
        long expResult = 123345L;
        long result = instance.getUidValidity();
        assertEquals(expResult, result);

    }

    /**
     * Test of hashCode method, of class HBaseMailbox.
     */
    @Test
    public void testHashCode() {
        System.out.println("hashCode");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 1234);
        // from the hashCode()
        final int PRIME = 31;
        int result = 1;
        HBaseId mailboxId = instance.getMailboxId();
        int expResult = PRIME * result + (int) (mailboxId.getRawId().getMostSignificantBits() ^ (mailboxId.getRawId().getMostSignificantBits() >>> 32));

        assertEquals(expResult, instance.hashCode());
    }

    /**
     * Test of equals method, of class HBaseMailbox.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 12345);
        final HBaseMailbox instance2 = new HBaseMailbox(mailboxPath, 12345);
        instance2.setMailboxId(instance.getMailboxId());
        assertEquals(instance, instance2);
    }

    /**
     * Test of consumeUid method, of class HBaseMailbox.
     */
    @Test
    public void testConsumeUid() {
        System.out.println("consumeUid");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 10);
        long expResult = instance.getLastUid() + 1;
        long result = instance.consumeUid();
        assertEquals(expResult, result);
    }

    /**
     * Test of consumeModSeq method, of class HBaseMailbox.
     */
    @Test
    public void testConsumeModSeq() {
        System.out.println("consumeModSeq");
        final MailboxPath mailboxPath = new MailboxPath("gsoc", "ieugen", "INBOX");
        final HBaseMailbox instance = new HBaseMailbox(mailboxPath, 10);
        long expResult = instance.getHighestModSeq() + 1;
        long result = instance.consumeModSeq();
        assertEquals(expResult, result);
    }
}
