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

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Palaga
 */
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
        user1Key = EntryKey.createUser(USER_1);
        user2Key = EntryKey.createUser(USER_2);
        group1Key = EntryKey.createGroup(GROUP_1);
        group2Key = EntryKey.createGroup(GROUP_2);

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
        user1ReadNegative = new MailboxACL(new Entry(EntryKey.createUser(USER_1, true), Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        group1Read = new MailboxACL(new Entry(group1Key, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        group1ReadNegative = new MailboxACL(new Entry(EntryKey.createGroup(GROUP_1, true), Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        anybodyRead = new MailboxACL(new Entry(MailboxACL.ANYBODY_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        anybodyReadNegative = new MailboxACL(new Entry(MailboxACL.ANYBODY_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        authenticatedRead = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        authenticatedReadNegative = new MailboxACL(new Entry(MailboxACL.AUTHENTICATED_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

        ownerRead = new MailboxACL(new Entry(MailboxACL.OWNER_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));
        ownerReadNegative = new MailboxACL(new Entry(MailboxACL.OWNER_NEGATIVE_KEY, Rfc4314Rights.fromSerializedRfc4314Rights("r")));

    }

    @Test
    public void testAppliesNullUser() throws UnsupportedRightException {

        Assert.assertFalse(UnionMailboxACLResolver.applies(user1Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group1Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, null, groupMembershipResolver, USER_1, false));
    }

    @Test
    public void testAppliesUser() throws UnsupportedRightException {
        /* requester is the resource owner */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_1, false));

        /* requester is not the resource user */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_2, false));

        /* requester member of owner group */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_1, true));

        /* requester not member of owner group */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_2, true));

        /* owner query */
        Assert.assertFalse(UnionMailboxACLResolver.applies(user1Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group1Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.ANYBODY_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.AUTHENTICATED_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(MailboxACL.OWNER_KEY, MailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));

    }

    @Test
    public void testHasRightNullUser() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

    }

    @Test
    public void testHasRightNullUserGlobals() throws UnsupportedRightException {
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, MailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, MailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, MailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, MailboxACL.Right.Read, MailboxACL.EMPTY, USER_2, false));
    }
    

    @Test
    public void testHasRightUserSelfOwner() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_1, false));

    }
    

    @Test
    public void testHasRightUserNotOwner() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, USER_2, false));

    }
    @Test
    public void testHasRightUserMemberOfOwnerGroup() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

    }    
    
    
    @Test
    public void testHasRightUserNotMemberOfOwnerGroup() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, MailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

    }

}
