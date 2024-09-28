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

package org.apache.james.mailbox.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnionMailboxACLResolverTest {
    private static final Username USER_1 = Username.of("user1");
    private static final Username USER_2 = Username.of("user2");

    private MailboxACL anyoneRead;
    private MailboxACL anyoneReadNegative;
    private UnionMailboxACLResolver anyoneReadListGlobal;
    private MailboxACL authenticatedRead;
    private UnionMailboxACLResolver authenticatedReadListWriteGlobal;
    private MailboxACL authenticatedReadNegative;
    private UnionMailboxACLResolver noGlobals;
    private UnionMailboxACLResolver ownerFullGlobal;
    private MailboxACL ownerRead;
    private MailboxACL ownerReadNegative;
    private MailboxACL user1Read;
    private MailboxACL user1ReadNegative;
    private EntryKey user1Key;
    private EntryKey user2Key;

    @BeforeEach
    void setUp() throws Exception {
        user1Key = EntryKey.createUserEntryKey(USER_1);
        user2Key = EntryKey.createUserEntryKey(USER_2);

        MailboxACL acl = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_KEY, MailboxACL.FULL_RIGHTS));
        authenticatedReadListWriteGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new MailboxACL(new Entry(MailboxACL.ANYONE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("rl")));
        anyoneReadListGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new MailboxACL(new Entry(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS));
        ownerFullGlobal = new UnionMailboxACLResolver(acl, acl);
        noGlobals = new UnionMailboxACLResolver(MailboxACL.EMPTY, MailboxACL.EMPTY);

        user1Read = new MailboxACL(new Entry(user1Key, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        user1ReadNegative = new MailboxACL(new Entry(EntryKey.createUserEntryKey(USER_1, MailboxACL.NEGATIVE_KEY), Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        anyoneRead = new MailboxACL(new Entry(MailboxACL.ANYONE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        anyoneReadNegative = new MailboxACL(new Entry(MailboxACL.ANYONE_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        authenticatedRead = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        authenticatedReadNegative = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        ownerRead = new MailboxACL(new Entry(MailboxACL.OWNER_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        ownerReadNegative = new MailboxACL(new Entry(MailboxACL.OWNER_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

    }

    @Test
    void testAppliesNullUser() throws UnsupportedRightException {

        assertThat(UnionMailboxACLResolver.applies(user1Key, null, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(user2Key, null, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYONE_KEY, null, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, null, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, null, USER_1)).isFalse();
    }

    @Test
    void testAppliesUser() throws UnsupportedRightException {
        /* requester is the resource owner */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYONE_KEY, user1Key, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, USER_1)).isTrue();

        /* requester is not the resource user */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, USER_2)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, USER_2)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYONE_KEY, user1Key, USER_2)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, USER_2)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, USER_2)).isFalse();

        /* owner query */
        assertThat(UnionMailboxACLResolver.applies(user1Key, MailboxACL.OWNER_KEY, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(user2Key, MailboxACL.OWNER_KEY, USER_1)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYONE_KEY, MailboxACL.OWNER_KEY, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, MailboxACL.OWNER_KEY, USER_1)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, MailboxACL.OWNER_KEY, USER_1)).isTrue();
    }

    @Test
    void testResolveRightsNullUser() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(null, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            anyoneReadListGlobal.resolveRights(null, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(null, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(null, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(null, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(null, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(null, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }

    @Test
    void testResolveRightsNullUserGlobals() throws UnsupportedRightException {
        assertThat(
            anyoneReadListGlobal.resolveRights(null, user1Read, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, MailboxACL.EMPTY, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, MailboxACL.EMPTY, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, MailboxACL.EMPTY, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
    }


    @Test
    void testResolveRightsUserSelfOwner() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, user1Read, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, user1ReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, anyoneRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, anyoneReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, authenticatedRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, authenticatedReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, ownerRead, USER_1)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, ownerReadNegative, USER_1)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }


    @Test
    void testResolveRightsUserNotOwner() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, user1Read, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, user1ReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, user1Read, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, user1ReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, user1Read, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, user1ReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, user1Read, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, user1ReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, anyoneRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, anyoneReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, anyoneRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, anyoneReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, anyoneRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, anyoneReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, anyoneRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, anyoneReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, authenticatedRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, authenticatedRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, authenticatedRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, authenticatedReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, authenticatedRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, authenticatedReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, ownerRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, ownerReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, ownerRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, ownerReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, ownerRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, ownerReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, ownerRead, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(USER_1, ownerReadNegative, USER_2)
                .contains(MailboxACL.Right.Read))
            .isFalse();
    }
}
