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

import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.SpecialName;
import org.junit.jupiter.api.Test;

class MailboxACLEntryKeyTest {
    private static final String GROUP_1 = "group1";
    private static final String USER_1 = "user1";

    @Test
    void testUser() {
        assertThat(EntryKey.deserialize(USER_1))
            .isEqualTo(new EntryKey(USER_1, NameType.user, false));
    }

    @Test
    void testNegativeUser() {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1))
            .isEqualTo(new EntryKey(USER_1, NameType.user, true));
    }

    @Test
    void testGroup() {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1))
            .isEqualTo(new EntryKey(GROUP_1, NameType.group, false));
    }

    @Test
    void testNegativeGroup() {
        assertThat(EntryKey.deserialize(String.valueOf(MailboxACL.DEFAULT_NEGATIVE_MARKER) + MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1))
            .isEqualTo(new EntryKey(GROUP_1, NameType.group, true));
    }

    @Test
    void testOwner() {
        assertThat(EntryKey.deserialize(SpecialName.owner.toString()))
            .isEqualTo(new EntryKey(SpecialName.owner.toString(), NameType.special, false));
    }

    @Test
    void testNegativeOwner() {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.owner.toString()))
            .isEqualTo(new EntryKey(SpecialName.owner.toString(), NameType.special, true));
    }

    @Test
    void testAnybody() {
        assertThat(EntryKey.deserialize(SpecialName.anybody.toString()))
            .isEqualTo(new EntryKey(SpecialName.anybody.toString(), NameType.special, false));
    }

    @Test
    void testNegativeAnybody() {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.anybody.toString()))
            .isEqualTo(new EntryKey(SpecialName.anybody.toString(), NameType.special, true));
    }

    @Test
    void testAuthenticated() {
        assertThat(EntryKey.deserialize(SpecialName.authenticated.toString()))
            .isEqualTo(new EntryKey(SpecialName.authenticated.toString(), NameType.special, false));
    }

    @Test
    void testNegativeAuthenticated() {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.authenticated.toString()))
            .isEqualTo(new EntryKey(SpecialName.authenticated.toString(), NameType.special, true));
    }

    @Test
    void testSerializeUser() {
        assertThat(new EntryKey(USER_1, NameType.user, false).serialize())
            .isEqualTo(USER_1);
    }

    @Test
    void testSerializeNegativeUser() {
        assertThat(new EntryKey(USER_1, NameType.user, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1);
    }

    @Test
    void testSerializeGroup() {
        assertThat(new EntryKey(GROUP_1, NameType.group, false).serialize())
            .isEqualTo(MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
    }

    @Test
    void testSerializeNegativeGroup() {
        assertThat(new EntryKey(GROUP_1, NameType.group, true).serialize())
            .isEqualTo(String.valueOf(MailboxACL.DEFAULT_NEGATIVE_MARKER) + MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
    }

    @Test
    void testSerializeOwner() {
        assertThat(new EntryKey(SpecialName.owner.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.owner.toString());
    }

    @Test
    void testSerializeNegativeOwner() {
        assertThat(new EntryKey(SpecialName.owner.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.owner.toString());
    }

    @Test
    void testSerializeAnybody() {
        assertThat(new EntryKey(SpecialName.anybody.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.anybody.toString());
    }

    @Test
    void testSerializeNegativeAnybody() {
        assertThat(new EntryKey(SpecialName.anybody.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.anybody.toString());
    }

    @Test
    void testSerializeAuthenticated() {
        assertThat(new EntryKey(SpecialName.authenticated.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.authenticated.toString());
    }

    @Test
    void testSerializeNegativeAuthenticated() {
        assertThat(new EntryKey(SpecialName.authenticated.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.authenticated.toString());
    }
}
