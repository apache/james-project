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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxACLTest {

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";
    private static final Username USERNAME_1 = Username.of("user1");
    private static final Username USERNAME_2 = Username.of("user2");
    private static final boolean NEGATIVE = true;

    private static final String ae = "ae";
    private static final String ik = "ik";
    private static final String aeik = "aeik";
    private static final String lprs = "lprs";
    private static final String twx = "twx";

    private Properties u1u2g1g2Properties;

    private MailboxACL u1u2g1g2ACL;

    @BeforeEach
    void setUp() throws Exception {

        u1u2g1g2Properties = new Properties();

        u1u2g1g2Properties.setProperty(USER_1, aeik);
        u1u2g1g2Properties.setProperty(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1, lprs);
        u1u2g1g2Properties.setProperty(USER_2, lprs);
        u1u2g1g2Properties.setProperty(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_2, twx);

        u1u2g1g2ACL = new MailboxACL(u1u2g1g2Properties);

    }

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MailboxACL.class).verify();
    }

    @Test
    void rfc4314RightsShouldRespectBeanContract() {
        EqualsVerifier.forClass(MailboxACL.Rfc4314Rights.class).verify();
    }

    @Test
    void testUnionACLNew() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        MailboxACL toAdd = MailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(expectedEntries).isEqualTo(foundEntries);
    }

    @Test
    void testUnionEntryNew() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        MailboxACL result = u1u2g1g2ACL.union(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void testUnionACLExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(aeik + lprs));

        MailboxACL toAdd = new MailboxACL(new Entry(USER_1, lprs));
        MailboxACL result = u1u2g1g2ACL.union(toAdd);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(expectedEntries).isEqualTo(foundEntries);
    }

    @Test
    void testUnionEntryExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(aeik + lprs));

        MailboxACL result = u1u2g1g2ACL.union(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(lprs));

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void testUnionACLZero() throws UnsupportedRightException {

    }

    @Test
    void testUnionEntryZero() throws UnsupportedRightException {

    }

    @Test
    void testExceptACLNew() throws UnsupportedRightException {

        /* actually no change expected */
        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL toRemove = MailboxACL.OWNER_FULL_ACL;
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(expectedEntries).isEqualTo(foundEntries);
    }

    @Test
    void testExceptEntryNew() throws UnsupportedRightException {

        /* actually no change expected */
        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());

        MailboxACL result = u1u2g1g2ACL.except(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(expectedEntries).isEqualTo(foundEntries);
    }

    @Test
    void testExceptACLExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ik));

        MailboxACL toRemove = new MailboxACL(new Entry(USER_1, ae));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void testExceptEntryExisting() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.put(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ik));

        MailboxACL result = u1u2g1g2ACL.except(EntryKey.deserialize(USER_1), Rfc4314Rights.fromSerializedRfc4314Rights(ae));

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void testExceptACLFull() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(EntryKey.deserialize(USER_1));

        MailboxACL toRemove = new MailboxACL(new Entry(USER_1, MailboxACL.FULL_RIGHTS.serialize()));
        MailboxACL result = u1u2g1g2ACL.except(toRemove);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void testExceptEntryFull() throws UnsupportedRightException {

        Map<EntryKey, Rfc4314Rights> expectedEntries = new HashMap<>(u1u2g1g2ACL.getEntries());
        expectedEntries.remove(EntryKey.deserialize(USER_1));

        MailboxACL result = u1u2g1g2ACL.except(EntryKey.deserialize(USER_1), MailboxACL.FULL_RIGHTS);

        Map<EntryKey, Rfc4314Rights> foundEntries = result.getEntries();

        assertThat(foundEntries).isEqualTo(expectedEntries);
    }

    @Test
    void propertiesConstructorShouldAcceptNullValues() throws Exception {
        assertThat(new MailboxACL((Properties) null))
            .isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void applyShouldNotThrowWhenRemovingANonExistingEntry() throws Exception {
        assertThat(MailboxACL.EMPTY
            .apply(MailboxACL.command().forUser(Username.of("bob")).noRights().asReplacement()))
            .isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void usersACLShouldReturnEmptyMapWhenEmpty() {
        assertThat(MailboxACL.EMPTY.ofPositiveNameType(NameType.user))
            .isEmpty();
    }

    @Test
    void usersACLShouldReturnEmptyMapWhenNoUserEntry() {
        MailboxACL mailboxACL = new MailboxACL(
                ImmutableMap.of(EntryKey.createGroupEntryKey("group"), MailboxACL.FULL_RIGHTS,
                    EntryKey.createGroupEntryKey("group2"), MailboxACL.NO_RIGHTS));
        assertThat(mailboxACL.ofPositiveNameType(NameType.user))
            .isEmpty();
    }

    @Test
    void usersACLShouldReturnOnlyUsersMapWhenSomeUserEntries() throws Exception {
        MailboxACL.Rfc4314Rights rights = MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("aei");
        MailboxACL mailboxACL = new MailboxACL(
            ImmutableMap.of(EntryKey.createUserEntryKey(USERNAME_1), MailboxACL.FULL_RIGHTS,
                EntryKey.createGroupEntryKey("group"), MailboxACL.FULL_RIGHTS,
                EntryKey.createUserEntryKey(USERNAME_2), rights,
                EntryKey.createGroupEntryKey("group2"), MailboxACL.NO_RIGHTS));
        assertThat(mailboxACL.ofPositiveNameType(NameType.user))
            .containsOnly(
                MapEntry.entry(EntryKey.createUserEntryKey(USERNAME_1), MailboxACL.FULL_RIGHTS),
                MapEntry.entry(EntryKey.createUserEntryKey(USERNAME_2), rights));
    }

    @Test
    void ofPositiveNameTypeShouldFilterOutNegativeEntries() throws Exception {
        MailboxACL mailboxACL = new MailboxACL(
            ImmutableMap.of(EntryKey.createUserEntryKey(Username.of("user1"), NEGATIVE), MailboxACL.FULL_RIGHTS));
        assertThat(mailboxACL.ofPositiveNameType(NameType.user))
            .isEmpty();
    }
}
