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
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntry;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntryKey;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class SimpleMailboxACLTest {

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

        u1u2g1g2ACL = new SimpleMailboxACL(u1u2g1g2Properties);

    }

    @Test
    public void testUnionACLNew() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS);

        MailboxACL toAdd = SimpleMailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testUnionEntryNew() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS);

        MailboxACL result = u1u2g1g2ACL.union(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertThat(foundEntries)
            .hasSize(expectedEntries.size())
            .containsAllEntriesOf(expectedEntries);
    }

    @Test
    public void testUnionACLExisting() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(aeik + lprs));

        MailboxACL toAdd = new SimpleMailboxACL(new SimpleMailboxACLEntry(USER_1, lprs));
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testUnionEntryExisting() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(aeik + lprs));

        MailboxACL result = u1u2g1g2ACL.union(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(lprs));

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

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
        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL toRemove = SimpleMailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testExceptEntryNew() throws UnsupportedRightException {

        /* actually no change expected */
        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL result = u1u2g1g2ACL.except(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertEquals(foundEntries, expectedEntries);
    }

    @Test
    public void testExceptACLExisting() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(ik));

        MailboxACL toRemove = new SimpleMailboxACL(new SimpleMailboxACLEntry(USER_1, ae));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertThat(foundEntries)
            .hasSize(expectedEntries.size())
            .containsAllEntriesOf(expectedEntries);
    }

    @Test
    public void testExceptEntryExisting() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(ik));

        MailboxACL result = u1u2g1g2ACL.except(SimpleMailboxACLEntryKey.deserialize(USER_1), new Rfc4314Rights(ae));

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertThat(foundEntries)
            .hasSize(expectedEntries.size())
            .containsAllEntriesOf(expectedEntries);
    }

    @Test
    public void testExceptACLFull() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(SimpleMailboxACLEntryKey.deserialize(USER_1));

        MailboxACL toRemove = new SimpleMailboxACL(new SimpleMailboxACLEntry(USER_1, SimpleMailboxACL.FULL_RIGHTS.serialize()));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertThat(foundEntries)
            .hasSize(expectedEntries.size())
            .containsAllEntriesOf(expectedEntries);
    }

    @Test
    public void testExceptEntryFull() throws UnsupportedRightException {

        Map<MailboxACLEntryKey, MailboxACLRights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(SimpleMailboxACLEntryKey.deserialize(USER_1));

        MailboxACL result = u1u2g1g2ACL.except(SimpleMailboxACLEntryKey.deserialize(USER_1), SimpleMailboxACL.FULL_RIGHTS);

        Map<MailboxACLEntryKey, MailboxACLRights> foundEntries = result.getEntries();

        assertThat(foundEntries)
            .hasSize(expectedEntries.size())
            .containsAllEntriesOf(expectedEntries);
    }

}
