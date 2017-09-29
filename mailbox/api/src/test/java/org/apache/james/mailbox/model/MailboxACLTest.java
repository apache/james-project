/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class MailboxACLTest {

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";

    private static final String ae = "ae";
    private static final String ik = "ik";
    private static final String aeik = "aeik";
    private static final String lprs = "lprs";
    private static final String twx = "twx";

    private Properties u1u2g1g2Properties;

    private MailboxACL u1u2g1g2ACL;

    @Before
    public void setUp() throws Exception {

        u1u2g1g2Properties = new Properties();

        u1u2g1g2Properties.setProperty(USER_1, aeik);
        u1u2g1g2Properties.setProperty(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1, lprs);
        u1u2g1g2Properties.setProperty(USER_2, lprs);
        u1u2g1g2Properties.setProperty(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_2, twx);

        u1u2g1g2ACL = new MailboxACL(u1u2g1g2Properties);

    }

    @Test
    public void testUnionACLNew() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        MailboxACL toAdd = MailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testUnionEntryNew() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        MailboxACL result = u1u2g1g2ACL.union(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void testUnionACLExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(aeik + lprs));

        MailboxACL toAdd = new MailboxACL(new Entry(USER_1, lprs));
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testUnionEntryExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(aeik + lprs));

        MailboxACL result = u1u2g1g2ACL.union(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(lprs));

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testUnionACLZero() throws UnsupportedRightException {

    }

    @Test
    public void testUnionEntryZero() throws UnsupportedRightException {

    }

    @Test
    public void testExceptACLNew() throws UnsupportedRightException {

        /* actually no change expected */
        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL toRemove = MailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testExceptEntryNew() throws UnsupportedRightException {

        /* actually no change expected */
        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL result = u1u2g1g2ACL.except(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testExceptACLExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ik));

        MailboxACL toRemove = new MailboxACL(new Entry(USER_1, ae));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void testExceptEntryExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ik));

        MailboxACL result = u1u2g1g2ACL.except(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ae));

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void testExceptACLFull() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(EntryKey.deserialize(USER_1));

        MailboxACL toRemove = new MailboxACL(new Entry(USER_1, MailboxACL.FULL_RIGHTS.serialize()));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void testExceptEntryFull() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(EntryKey.deserialize(USER_1));

        MailboxACL result = u1u2g1g2ACL.except(EntryKey.deserialize(USER_1), MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void propertiesConstructorShouldAcceptNullValues() throws Exception {
        assertThat(new MailboxACL((Properties) null))
            .isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    public void applyShouldNotThrowWhenRemovingANonExistingEntry() throws Exception{
        assertThat(MailboxACL.EMPTY
            .apply(MailboxACL.command().forUser("bob").noRights().asReplacement()))
            .isEqualTo(MailboxACL.EMPTY);
    }

}
