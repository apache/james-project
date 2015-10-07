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

import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_ANSWERED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_DELETED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_DRAFT;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_FLAGGED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_RECENT;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_SEEN;
import static org.apache.james.mailbox.hbase.FlagConvertor.userFlagToBytes;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_HIGHEST_MODSEQ;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_LASTUID;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_MESSAGE_COUNT;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAME;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAMESPACE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_UIDVALIDITY;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_USER;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_MISSING;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_PRESENT;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;
import static org.apache.james.mailbox.hbase.HBaseUtils.HBaseIdFromRowKey;
import static org.apache.james.mailbox.hbase.HBaseUtils.flagsToPut;
import static org.apache.james.mailbox.hbase.HBaseUtils.toPut;
import static org.apache.james.mailbox.hbase.PropertyConvertor.getProperty;
import static org.apache.james.mailbox.hbase.PropertyConvertor.getValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.UUID;

import javax.mail.Flags;

import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.mailbox.store.user.model.impl.SimpleSubscription;
import org.junit.Test;

/**
 * Tests for HBase Mailbox store utility methods .
 */
public class HBaseUtilsTest {
    
    /**
     * Test of mailboxRowKey method, of class HBaseMailbox.
     */
    @Test
    public void testRowKey_All() {
        System.out.println("getRowKey and UUIDFromRowKey");
        final HBaseMailbox mailbox = new HBaseMailbox(new MailboxPath("gsoc", "ieugen", "INBOX"), 1234);
        HBaseId uuid = mailbox.getMailboxId();
        byte[] expResult = uuid.toBytes();
        byte[] result = mailbox.getMailboxId().toBytes();
        assertArrayEquals(expResult, result);

        HBaseId newUUID = HBaseIdFromRowKey(result);
        assertEquals(uuid, newUUID);

        newUUID = HBaseIdFromRowKey(expResult);
        assertEquals(uuid, newUUID);
    }

    /**
     * Test of metadataToPut method, of class HBaseMailbox.
     */
    @Test
    public void testMailboxToPut() {
        System.out.println("mailboxToPut");
        final HBaseMailbox instance = new HBaseMailbox(new MailboxPath("gsoc", "ieugen", "INBOX"), 1234);

        Put result = toPut(instance);
        assertArrayEquals(instance.getMailboxId().toBytes(), result.getRow());
        assertTrue(result.has(MAILBOX_CF, MAILBOX_USER, Bytes.toBytes(instance.getUser())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_NAME, Bytes.toBytes(instance.getName())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_NAMESPACE, Bytes.toBytes(instance.getNamespace())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_UIDVALIDITY, Bytes.toBytes(instance.getUidValidity())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_LASTUID, Bytes.toBytes(instance.getLastUid())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, Bytes.toBytes(instance.getHighestModSeq())));
        assertTrue(result.has(MAILBOX_CF, MAILBOX_MESSAGE_COUNT, Bytes.toBytes(0L)));
    }

    /**
     * Test of metadataToPut method for message.
     */
//    @Test
    public void testMessageToPut() {
        System.out.println("messageToPut");
        // left to implement
    }

    @Test
    public void testPropertyToBytes() {
        final Property prop1 = new SimpleProperty("nspace", "localName", "test");
        byte[] value = getValue(prop1);
        final Property prop2 = getProperty(value);
        assertEquals(prop1.getNamespace(), prop2.getNamespace());
        assertEquals(prop1.getLocalName(), prop2.getLocalName());
        assertEquals(prop1.getValue(), prop2.getValue());
    }

    @Test
    public void testSubscriptionToPut() {
        System.out.println("subscription toPut");
        Subscription subscription = new SimpleSubscription("ieugen", "INBOX");
        Put put = toPut(subscription);
        assertArrayEquals(Bytes.toBytes(subscription.getUser()), put.getRow());
        assertTrue(put.has(SUBSCRIPTION_CF, Bytes.toBytes(subscription.getMailbox()), MARKER_PRESENT));
    }

    @Test
    public void testFlagsToPut() {
        System.out.println("flagsToPut");

        final Flags flags = new Flags();
        flags.add(Flags.Flag.SEEN);
        flags.add(Flags.Flag.DRAFT);
        flags.add(Flags.Flag.RECENT);
        flags.add(Flags.Flag.FLAGGED);
        flags.add("userFlag1");
        flags.add("userFlag2");
        HBaseId uuid = HBaseId.of(UUID.randomUUID());
        final SimpleMessage<HBaseId> message = new SimpleMessage<HBaseId>(new Date(), 100, 10, null, flags, new PropertyBuilder(), uuid);
        Put put = flagsToPut(message, flags);
        //test for the system flags
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_SEEN, MARKER_PRESENT));
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_DRAFT, MARKER_PRESENT));
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_RECENT, MARKER_PRESENT));
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_FLAGGED, MARKER_PRESENT));
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_ANSWERED, MARKER_MISSING));
        assertTrue(put.has(MESSAGES_META_CF, FLAGS_DELETED, MARKER_MISSING));
        assertTrue(put.has(MESSAGES_META_CF, userFlagToBytes("userFlag1"), MARKER_PRESENT));
        assertTrue(put.has(MESSAGES_META_CF, userFlagToBytes("userFlag2"), MARKER_PRESENT));
    }
}
