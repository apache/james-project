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

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.Before;
import org.junit.Test;

public class UnionMailboxACLResolverTest {

    private static final String GROUP_1 = "group1";
    private static final String GROUP_2 = "group2";

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";

    private MailboxACL anybodyRead;
    private MailboxACL anybodyReadNegative;
    private UnionMailboxACLResolver anyoneReadListGlobal;
    private MailboxACL authenticatedRead;
    private UnionMailboxACLResolver authenticatedReadListWriteGlobal;
    private MailboxACL authenticatedReadNegative;
    private MailboxACL group1Read;
    private MailboxACL group1ReadNegative;
    private SimpleGroupMembershipResolver groupMembershipResolver;
    private UnionMailboxACLResolver negativeGroup2FullGlobal;
    private UnionMailboxACLResolver noGlobals;
    private UnionMailboxACLResolver ownerFullGlobal;
    private MailboxACL ownerRead;
    private MailboxACL ownerReadNegative;
    private MailboxACL user1Read;
    private MailboxACL user1ReadNegative;
    private EntryKey user1Key;
    private EntryKey user2Key;
    private EntryKey group1Key;
    private EntryKey group2Key;

    @Before
    public void setUp() throws Exception {
        user1Key = EntryKey.createUserEntryKey(USER_1);
        user2Key = EntryKey.createUserEntryKey(USER_2);
        group1Key = EntryKey.createGroupEntryKey(GROUP_1);
        group2Key = EntryKey.createGroupEntryKey(GROUP_2);

        MailboxACL acl = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_KEY, MailboxACL.FULL_RIGHTS));
        authenticatedReadListWriteGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new MailboxACL(new Entry(MailboxACL.ANYBODY_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("rl")));
        anyoneReadListGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new MailboxACL(new Entry(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS));
        ownerFullGlobal = new UnionMailboxACLResolver(acl, acl);
        noGlobals = new UnionMailboxACLResolver(MailboxACL.EMPTY, MailboxACL.EMPTY);
        acl = new MailboxACL(new Entry(new EntryKey(GROUP_2, NameType.group, true), MailboxACL.FULL_RIGHTS));
        negativeGroup2FullGlobal = new UnionMailboxACLResolver(acl, new MailboxACL(new Entry(new EntryKey(GROUP_2, NameType.group, true), MailboxACL.FULL_RIGHTS)));

        groupMembershipResolver = new SimpleGroupMembershipResolver();
        groupMembershipResolver.addMembership(GROUP_1, USER_1);
        groupMembershipResolver.addMembership(GROUP_2, USER_2);

        user1Read = new MailboxACL(new Entry(user1Key, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        user1ReadNegative = new MailboxACL(new Entry(EntryKey.createUserEntryKey(USER_1, true), Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        group1Read = new MailboxACL(new Entry(group1Key, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        group1ReadNegative = new MailboxACL(new Entry(EntryKey.createGroupEntryKey(GROUP_1, true), Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        anybodyRead = new MailboxACL(new Entry(MailboxACL.ANYBODY_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        anybodyReadNegative = new MailboxACL(new Entry(MailboxACL.ANYBODY_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        authenticatedRead = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        authenticatedReadNegative = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        ownerRead = new MailboxACL(new Entry(MailboxACL.OWNER_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        ownerReadNegative = new MailboxACL(new Entry(MailboxACL.OWNER_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

    }

    @Test
    public void testAppliesNullUser() throws UnsupportedRightException {

        assertThat(UnionMailboxACLResolver.applies(user1Key, null, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(user2Key, null, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, null, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group2Key, null, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, null, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, null, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, null, groupMembershipResolver, USER_1, false)).isFalse();
    }

    @Test
    public void testAppliesUser() throws UnsupportedRightException {
        /* requester is the resource owner */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_1, false)).isTrue();

        /* requester is not the resource user */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_2, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_2, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_2, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_2, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_2, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_2, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_2, false)).isFalse();

        /* requester member of owner group */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_1, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_1, true)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_1, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_1, true)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_1, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_1, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_1, true)).isTrue();

        /* requester not member of owner group */
        assertThat(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_2, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_2, true)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_2, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_2, true)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_2, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_2, true)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_2, true)).isFalse();

        /* owner query */
        assertThat(UnionMailboxACLResolver.applies(user1Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(user2Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group1Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(group2Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isFalse();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isTrue();
        assertThat(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false)).isTrue();

    }

    @Test
    public void testResolveRightsNullUser() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }

    @Test
    public void testResolveRightsNullUserGlobals() throws UnsupportedRightException {
        assertThat(
            anyoneReadListGlobal.resolveRights(null, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(null, groupMembershipResolver, MailboxACL.EMPTY, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(null, groupMembershipResolver, MailboxACL.EMPTY, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(null, groupMembershipResolver, MailboxACL.EMPTY, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(null, groupMembershipResolver, MailboxACL.EMPTY, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
    }


    @Test
    public void testResolveRightsUserSelfOwner() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_1, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }


    @Test
    public void testResolveRightsUserNotOwner() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, USER_2, false)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }

    @Test
    public void testResolveRightsUserMemberOfOwnerGroup() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_1, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }


    @Test
    public void testResolveRightsUserNotMemberOfOwnerGroup() throws UnsupportedRightException {

        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, user1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1Read, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, group1ReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, anybodyReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, authenticatedReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();


        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            anyoneReadListGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();
        assertThat(
            authenticatedReadListWriteGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isTrue();

        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            ownerFullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            noGlobals.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerRead, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();
        assertThat(
            negativeGroup2FullGlobal.resolveRights(USER_1, groupMembershipResolver, ownerReadNegative, GROUP_2, true)
                .contains(MailboxACL.Right.Read))
            .isFalse();

    }
}
