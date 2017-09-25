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
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntry;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntryKey;
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
    private SimpleMailboxACLEntryKey user1Key;
    private SimpleMailboxACLEntryKey user2Key;
    private SimpleMailboxACLEntryKey group1Key;
    private SimpleMailboxACLEntryKey group2Key;

    @Before
    public void setUp() throws Exception {
        user1Key = SimpleMailboxACLEntryKey.createUser(USER_1);
        user2Key = SimpleMailboxACLEntryKey.createUser(USER_2);
        group1Key = SimpleMailboxACLEntryKey.createGroup(GROUP_1);
        group2Key = SimpleMailboxACLEntryKey.createGroup(GROUP_2);
        
        MailboxACL acl = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.AUTHENTICATED_KEY, SimpleMailboxACL.FULL_RIGHTS));
        authenticatedReadListWriteGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.ANYBODY_KEY, new Rfc4314Rights("rl")));
        anyoneReadListGlobal = new UnionMailboxACLResolver(acl, acl);
        acl = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS));
        ownerFullGlobal = new UnionMailboxACLResolver(acl, acl);
        noGlobals = new UnionMailboxACLResolver(SimpleMailboxACL.EMPTY, SimpleMailboxACL.EMPTY);
        acl = new SimpleMailboxACL(new SimpleMailboxACLEntry(new SimpleMailboxACLEntryKey(GROUP_2, NameType.group, true), SimpleMailboxACL.FULL_RIGHTS));
        negativeGroup2FullGlobal = new UnionMailboxACLResolver(acl, new SimpleMailboxACL(new SimpleMailboxACLEntry(new SimpleMailboxACLEntryKey(GROUP_2, NameType.group, true), SimpleMailboxACL.FULL_RIGHTS)));

        groupMembershipResolver = new SimpleGroupMembershipResolver();
        groupMembershipResolver.addMembership(GROUP_1, USER_1);
        groupMembershipResolver.addMembership(GROUP_2, USER_2);

        user1Read = new SimpleMailboxACL(new SimpleMailboxACLEntry(user1Key, new Rfc4314Rights("r")));
        user1ReadNegative = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACLEntryKey.createUser(USER_1, true), new Rfc4314Rights("r")));

        group1Read = new SimpleMailboxACL(new SimpleMailboxACLEntry(group1Key, new Rfc4314Rights("r")));
        group1ReadNegative = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACLEntryKey.createGroup(GROUP_1, true), new Rfc4314Rights("r")));

        anybodyRead = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.ANYBODY_KEY, new Rfc4314Rights("r")));
        anybodyReadNegative = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.ANYBODY_NEGATIVE_KEY, new Rfc4314Rights("r")));

        authenticatedRead = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.AUTHENTICATED_KEY, new Rfc4314Rights("r")));
        authenticatedReadNegative = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.AUTHENTICATED_NEGATIVE_KEY, new Rfc4314Rights("r")));

        ownerRead = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.OWNER_KEY, new Rfc4314Rights("r")));
        ownerReadNegative = new SimpleMailboxACL(new SimpleMailboxACLEntry(SimpleMailboxACL.OWNER_NEGATIVE_KEY, new Rfc4314Rights("r")));

    }

    @Test
    public void testAppliesNullUser() throws UnsupportedRightException {

        Assert.assertFalse(UnionMailboxACLResolver.applies(user1Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group1Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, null, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, null, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, null, groupMembershipResolver, USER_1, false));
    }

    @Test
    public void testAppliesUser() throws UnsupportedRightException {
        /* requester is the resource owner */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_1, false));

        /* requester is not the resource user */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, USER_2, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, USER_2, false));

        /* requester member of owner group */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_1, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_1, true));

        /* requester not member of owner group */
        Assert.assertTrue(UnionMailboxACLResolver.applies(user1Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(group1Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, user1Key, groupMembershipResolver, GROUP_2, true));
        Assert.assertFalse(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, user1Key, groupMembershipResolver, GROUP_2, true));

        /* owner query */
        Assert.assertFalse(UnionMailboxACLResolver.applies(user1Key, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(user2Key, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group1Key, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertFalse(UnionMailboxACLResolver.applies(group2Key, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.ANYBODY_KEY, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.AUTHENTICATED_KEY, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));
        Assert.assertTrue(UnionMailboxACLResolver.applies(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.OWNER_KEY, groupMembershipResolver, USER_1, false));

    }

    @Test
    public void testHasRightNullUser() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

    }

    @Test
    public void testHasRightNullUserGlobals() throws UnsupportedRightException {
        Assert.assertTrue(anyoneReadListGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, SimpleMailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, SimpleMailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, SimpleMailboxACL.EMPTY, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(null, groupMembershipResolver, SimpleMailboxACL.Right.Read, SimpleMailboxACL.EMPTY, USER_2, false));
    }
    

    @Test
    public void testHasRightUserSelfOwner() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_1, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_1, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_1, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_1, false));

    }
    

    @Test
    public void testHasRightUserNotOwner() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, USER_2, false));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, USER_2, false));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_2, false));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, USER_2, false));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, USER_2, false));

    }
    @Test
    public void testHasRightUserMemberOfOwnerGroup() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_1, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_1, true));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_1, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_1, true));

    }    
    
    
    @Test
    public void testHasRightUserNotMemberOfOwnerGroup() throws UnsupportedRightException {

        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1Read, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, user1ReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1Read, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, group1ReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, anybodyReadNegative, GROUP_2, true));

        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));

        Assert.assertTrue(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, authenticatedReadNegative, GROUP_2, true));
        
        
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertTrue(anyoneReadListGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertTrue(authenticatedReadListWriteGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(ownerFullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(noGlobals.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerRead, GROUP_2, true));
        Assert.assertFalse(negativeGroup2FullGlobal.hasRight(USER_1, groupMembershipResolver, SimpleMailboxACL.Right.Read, ownerReadNegative, GROUP_2, true));

    }

}
