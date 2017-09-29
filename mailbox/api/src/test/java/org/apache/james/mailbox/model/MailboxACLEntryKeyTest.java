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

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.SpecialName;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
public class MailboxACLEntryKeyTest {
    private static final String GROUP_1 = "group1";
    private static final String USER_1 = "user1";

    @Test
    public void testUser() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(USER_1))
            .isEqualTo(new EntryKey(USER_1, NameType.user, false));
    }

    @Test
    public void testNegativeUser() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1))
            .isEqualTo(new EntryKey(USER_1, NameType.user, true));
    }

    @Test
    public void testGroup() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1))
            .isEqualTo(new EntryKey(GROUP_1, NameType.group, false));
    }

    @Test
    public void testNegativeGroup() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(String.valueOf(MailboxACL.DEFAULT_NEGATIVE_MARKER) + MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1))
            .isEqualTo(new EntryKey(GROUP_1, NameType.group, true));
    }

    @Test
    public void testOwner() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(SpecialName.owner.toString()))
            .isEqualTo(new EntryKey(SpecialName.owner.toString(), NameType.special, false));
    }

    @Test
    public void testNegativeOwner() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.owner.toString()))
            .isEqualTo(new EntryKey(SpecialName.owner.toString(), NameType.special, true));
    }

    @Test
    public void testAnybody() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(SpecialName.anybody.toString()))
            .isEqualTo(new EntryKey(SpecialName.anybody.toString(), NameType.special, false));
    }

    @Test
    public void testNegativeAnybody() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.anybody.toString()))
            .isEqualTo(new EntryKey(SpecialName.anybody.toString(), NameType.special, true));
    }

    @Test
    public void testAuthenticated() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(SpecialName.authenticated.toString()))
            .isEqualTo(new EntryKey(SpecialName.authenticated.toString(), NameType.special, false));
    }

    @Test
    public void testNegativeAuthenticated() throws UnsupportedRightException {
        assertThat(EntryKey.deserialize(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.authenticated.toString()))
            .isEqualTo(new EntryKey(SpecialName.authenticated.toString(), NameType.special, true));
    }

    @Test
    public void testSerializeUser() throws UnsupportedRightException {
        assertThat(new EntryKey(USER_1, NameType.user, false).serialize())
            .isEqualTo(USER_1);
    }

    @Test
    public void testSerializeNegativeUser() throws UnsupportedRightException {
        assertThat(new EntryKey(USER_1, NameType.user, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + USER_1);
    }

    @Test
    public void testSerializeGroup() throws UnsupportedRightException {
        assertThat(new EntryKey(GROUP_1, NameType.group, false).serialize())
            .isEqualTo(MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
    }

    @Test
    public void testSerializeNegativeGroup() throws UnsupportedRightException {
        assertThat(new EntryKey(GROUP_1, NameType.group, true).serialize())
            .isEqualTo(String.valueOf(MailboxACL.DEFAULT_NEGATIVE_MARKER) + MailboxACL.DEFAULT_GROUP_MARKER + GROUP_1);
    }

    @Test
    public void testSerializeOwner() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.owner.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.owner.toString());
    }

    @Test
    public void testSerializeNegativeOwner() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.owner.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.owner.toString());
    }

    @Test
    public void testSerializeAnybody() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.anybody.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.anybody.toString());
    }

    @Test
    public void testSerializeNegativeAnybody() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.anybody.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.anybody.toString());
    }

    @Test
    public void testSerializeAuthenticated() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.authenticated.toString(), NameType.special, false).serialize())
            .isEqualTo(SpecialName.authenticated.toString());
    }

    @Test
    public void testSerializeNegativeAuthenticated() throws UnsupportedRightException {
        assertThat(new EntryKey(SpecialName.authenticated.toString(), NameType.special, true).serialize())
            .isEqualTo(MailboxACL.DEFAULT_NEGATIVE_MARKER + SpecialName.authenticated.toString());
    }
}
