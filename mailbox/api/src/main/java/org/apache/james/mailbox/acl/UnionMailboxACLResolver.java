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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRight;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.SimpleMailboxACL.SimpleMailboxACLEntryKey;


/**
 * An implementation which works with the union of the rights granted to the
 * applicable identifiers. Inspired by RFC 4314 Section 2.
 * 
 * In
 * {@link UnionMailboxACLResolver#resolveRights(String, org.apache.james.mailbox.MailboxACLResolver.GroupMembershipResolver, MailboxACL, String, boolean)}
 * all applicable negative and non-negative rights are union-ed separately and
 * the result is computed afterwards with
 * <code>nonNegativeUnion.except(negativeUnion)</code>.
 * 
 * Allows for setting distinct global ACL for users' mailboxes on one hand and
 * group (a.k.a shared) mailboxes on the other hand. E.g. the zero parameter
 * constructor uses full rights for user mailboxes and
 * full-except-administration rights for group mailboxes.
 * 
 */
public class UnionMailboxACLResolver implements MailboxACLResolver {
    public static final MailboxACL DEFAULT_GLOBAL_GROUP_ACL = SimpleMailboxACL.OWNER_FULL_EXCEPT_ADMINISTRATION_ACL;

    /**
     * Nothing else than full rights for the owner.
     */
    public static final MailboxACL DEFAULT_GLOBAL_USER_ACL = SimpleMailboxACL.OWNER_FULL_ACL;

    private static final int POSITIVE_INDEX = 0;
    private static final int NEGATIVE_INDEX = 1;

    private final MailboxACL groupGlobalACL;
    /**
     * Stores global ACL which is merged with ACL of every mailbox when
     * computing
     * {@link #rightsOf(String, org.apache.james.mailbox.MailboxACLResolver.GroupMembershipResolver, Mailbox)}
     * and
     * {@link #hasRight(String, Mailbox, MailboxACLRight, org.apache.james.mailbox.MailboxACLResolver.GroupMembershipResolver)}
     * .
     */
    private final MailboxACL userGlobalACL;

    /**
     * Creates a new instance of UnionMailboxACLResolver with
     * {@link #DEFAULT_GLOBAL_USER_ACL} as {@link #userGlobalACL} and
     * {@link #DEFAULT_GLOBAL_USER_ACL} as {@link #groupGlobalACL}.
     */
    public UnionMailboxACLResolver() {
        super();
        this.userGlobalACL = DEFAULT_GLOBAL_USER_ACL;
        this.groupGlobalACL = DEFAULT_GLOBAL_GROUP_ACL;
    }

    /**
     * Creates a new instance of UnionMailboxACLResolver with the given
     * globalACL.
     * 
     * @param groupGlobalACL
     * 
     * @param globalACL
     *            see {@link #userGlobalACL}, cannot be null.
     * @throws NullPointerException
     *             when globalACL is null.
     */
    public UnionMailboxACLResolver(MailboxACL userGlobalACL, MailboxACL groupGlobalACL) {
        super();
        if (userGlobalACL == null) {
            throw new NullPointerException("Missing userGlobalACL.");
        }
        if (groupGlobalACL == null) {
            throw new NullPointerException("Missing groupGlobalACL.");
        }
        this.userGlobalACL = userGlobalACL;
        this.groupGlobalACL = groupGlobalACL;
    }

    /**
     * Tells whether the given {@code aclKey} {@link MailboxACLEntryKey} is
     * applicable for the given {@code queryKey}.
     * 
     * There are two use cases for which this method was designed and tested:
     * 
     * (1) Calls from
     * {@link #hasRight(String, GroupMembershipResolver, MailboxACLRight, MailboxACL, String, boolean)}
     * and
     * {@link #resolveRights(String, GroupMembershipResolver, MailboxACL, String, boolean)}
     * in which the {@code queryKey} is a {@link NameType#user}.
     * 
     * (2) Calls from
     * {@link #listRights(MailboxACLEntryKey, GroupMembershipResolver, String, boolean)}
     * where {@code queryKey} can be anything including {@link NameType#user},
     * {@link NameType#group} and all {@link NameType#special} identifiers.
     * 
     * Clearly the set of cases which this method has to handle in (1) is a
     * proper subset of the cases handled in (2). See the javadoc on
     * {@link #listRights(MailboxACLEntryKey, GroupMembershipResolver, String, boolean)}
     * for more details.
     * 
     * @param aclKey
     * @param queryKey
     * @param groupMembershipResolver
     * @param resourceOwner
     * @param resourceOwnerIsGroup
     * @return
     */
    protected static boolean applies(MailboxACLEntryKey aclKey, MailboxACLEntryKey queryKey, GroupMembershipResolver groupMembershipResolver, String resourceOwner, boolean resourceOwnerIsGroup) {
        final String aclKeyName = aclKey.getName();
        final NameType aclKeyNameType = aclKey.getNameType();
        if (MailboxACL.SpecialName.anybody.name().equals(aclKeyName)) {
            /* this works also for unauthenticated users */
            return true;
        } else if (queryKey != null) {
            String queryUserOrGroupName = queryKey.getName();
            switch (queryKey.getNameType()) {
            case user:
                /* Authenticated users */
                switch (aclKeyNameType) {
                case special:
                    if (MailboxACL.SpecialName.authenticated.name().equals(aclKeyName)) {
                        /* non null query user is viewed as authenticated */
                        return true;
                    } else if (MailboxACL.SpecialName.owner.name().equals(aclKeyName)) {
                        return (!resourceOwnerIsGroup && queryUserOrGroupName.equals(resourceOwner)) || (resourceOwnerIsGroup && groupMembershipResolver.isMember(queryUserOrGroupName, resourceOwner));
                    } else {
                        /* should not happen unless the parent if is changed */
                        throw new IllegalStateException("Unexpected " + MailboxACL.SpecialName.class.getName() + "." + aclKeyName);
                    }
                case user:
                    return aclKeyName.equals(queryUserOrGroupName);
                case group:
                    return groupMembershipResolver.isMember(queryUserOrGroupName, aclKeyName);
                default:
                    throw new IllegalStateException("Unexpected " + NameType.class.getName() + "." + aclKeyNameType);
                }
            case group:
                /* query is a group */
                switch (aclKeyNameType) {
                case special:
                    if (MailboxACL.SpecialName.authenticated.name().equals(aclKeyName)) {
                        /*
                         * see the javadoc comment on listRights()
                         */
                        return true;
                    } else if (MailboxACL.SpecialName.owner.name().equals(aclKeyName)) {
                        return resourceOwnerIsGroup && queryUserOrGroupName.equals(resourceOwner);
                    } else {
                        /* should not happen unless the parent if is changed */
                        throw new IllegalStateException("Unexpected " + MailboxACL.SpecialName.class.getName() + "." + aclKeyName);
                    }
                case user:
                    /* query groups cannot match ACL users */
                    return false;
                case group:
                    return aclKeyName.equals(queryUserOrGroupName);
                default:
                    throw new IllegalStateException("Unexpected " + NameType.class.getName() + "." + aclKeyNameType);
                }
            case special:
                /* query is a special name */
                switch (aclKeyNameType) {
                case special:
                    if (aclKeyName.equals(queryUserOrGroupName)) {
                        /*
                         * authenticated matches authenticated and owner matches
                         * owner
                         */
                        return true;
                    } else if (MailboxACL.SpecialName.owner.name().equals(queryUserOrGroupName) && MailboxACL.SpecialName.authenticated.name().equals(aclKeyName)) {
                        /*
                         * query owner matches authenticated because owner will
                         * be resolved only if the user is authenticated
                         */
                        return true;
                    } else {
                        return false;
                    }
                case user:
                case group:
                    /* query specials cannot match ACL users or groups */
                    return false;
                default:
                    throw new IllegalStateException("Unexpected " + NameType.class.getName() + "." + aclKeyNameType);
                }
            default:
                throw new IllegalStateException("Unexpected " + NameType.class.getName() + "." + queryKey.getNameType());
            }
        } else {
            /* non-anybody ACL keys do not match non-authenticated queries */
            return false;
        }
    }

    /**
     * @see org.apache.james.mailbox.MailboxACLResolver#applyGlobalACL(org.apache
     *      .james.mailbox.MailboxACL, boolean)
     */
    @Override
    public MailboxACL applyGlobalACL(MailboxACL resourceACL, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        return resourceOwnerIsGroup ? resourceACL.union(groupGlobalACL) : resourceACL.union(userGlobalACL);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxACLResolver#hasRight(java.
     *      lang.String, org.apache.james.mailbox.store.mail.MailboxACLResolver.
     *      GroupMembershipResolver,
     *      org.apache.james.mailbox.MailboxACL.MailboxACLRight,
     *      org.apache.james.mailbox.MailboxACL, java.lang.String)
     */
    @Override
    public boolean hasRight(String requestUser, GroupMembershipResolver groupMembershipResolver, MailboxACLRight right, MailboxACL resourceACL, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        final MailboxACLEntryKey queryKey = requestUser == null ? null : new SimpleMailboxACLEntryKey(requestUser, NameType.user, false);
        boolean result = false;
        Map<MailboxACLEntryKey, MailboxACLRights> entries = resourceOwnerIsGroup ? groupGlobalACL.getEntries() : userGlobalACL.getEntries();
        if (entries != null) {
            for (Entry<MailboxACLEntryKey, MailboxACLRights> entry : entries.entrySet()) {
                final MailboxACLEntryKey key = entry.getKey();
                if (applies(key, queryKey, groupMembershipResolver, resourceOwner, resourceOwnerIsGroup) && entry.getValue().contains(right)) {
                    if (key.isNegative()) {
                        return false;
                    } else {
                        result = true;
                    }
                }
            }
        }

        if (resourceACL != null) {
            entries = resourceACL.getEntries();
            if (entries != null) {
                for (Entry<MailboxACLEntryKey, MailboxACLRights> entry : entries.entrySet()) {
                    final MailboxACLEntryKey key = entry.getKey();
                    if (applies(key, queryKey, groupMembershipResolver, resourceOwner, resourceOwnerIsGroup) && entry.getValue().contains(right)) {
                        if (key.isNegative()) {
                            return false;
                        } else {
                            result = true;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * @see org.apache.james.mailbox.acl.MailboxACLResolver#isReadWrite(org.apache.james.mailbox.model.MailboxACL.MailboxACLRights,
     *      javax.mail.Flags)
     */
    @Override
    public boolean isReadWrite(MailboxACLRights mailboxACLRights, Flags sharedFlags) throws UnsupportedRightException {
        /* the two fast cases first */
        if (mailboxACLRights.contains(SimpleMailboxACL.Right.Insert) || mailboxACLRights.contains(SimpleMailboxACL.Right.PerformExpunge)) {
            return true;
        }
        /*
         * then go through shared flags. RFC 4314 section 4:
         * 
         * Changing flags: STORE
         * 
         * - the server MUST check if the user has "t" right
         * 
         * - when the user modifies \Deleted flag "s" right
         * 
         * - when the user modifies \Seen flag "w" right - for all other message
         * flags.
         */
        else if (sharedFlags != null) {
            if (sharedFlags.contains(Flag.DELETED) && mailboxACLRights.contains(SimpleMailboxACL.Right.DeleteMessages)) {
                return true;
            } else if (sharedFlags.contains(Flag.SEEN) && mailboxACLRights.contains(SimpleMailboxACL.Right.WriteSeenFlag)) {
                return true;
            } else {
                boolean hasWriteRight = mailboxACLRights.contains(SimpleMailboxACL.Right.Write);
                return hasWriteRight && (sharedFlags.contains(Flag.ANSWERED) || sharedFlags.contains(Flag.DRAFT) || sharedFlags.contains(Flag.FLAGGED) || sharedFlags.contains(Flag.RECENT) || sharedFlags.contains(Flag.USER));
            }
        }
        return false;
    }

    /**
     * The key point of this implementation is that it resolves everything what
     * can be resolved. Let us explain what it means in particular for the
     * implicit (global) rights included in the result:
     * 
     * (1) if {@code queryKey} is a user key, the rights included come from the
     * following ACL entries:
     * <ul>
     * <li>the entry literally matching the given user name</li>
     * <li>the entries of the groups of which the given user is a member</li>
     * <li>if the given user is the owner of the given mailbox also the "owner"
     * entry is included</li>
     * <li>the "authenticated" entry</li>
     * <li>the "anybody" entry</li>
     * </ul>
     * 
     * (2) if {@code queryKey} is a group key, the rights included come from the
     * following ACL entries:
     * <ul>
     * <li>the entry literally matching the given group name</li>
     * <li>if the given group is the owner of the given mailbox also the "owner"
     * entry is included</li>
     * <li>the "authenticated" entry (*)</li>
     * <li>the "anybody" entry</li>
     * </ul>
     * 
     * (3) if {@code queryKey} is a special key, the rights included come from
     * the following ACL entries:
     * <ul>
     * <li>the entry literally matching the given special name</li>
     * <li>the "authenticated" entry if the {@code queryKey} is the "owner"
     * query key (*)</li>
     * <li>the "anybody" entry</li>
     * </ul>
     * 
     * (*) This is the most questionable case: should "authenticated" ACL
     * entries hold for group name queries? We say yes. Firstly, listing
     * implicit rights for, say "group1", should inform which rights do not need
     * to be set explicitly for the members of "group1". And secondly the group
     * rights are actually queried and applied only for authenticated users. To
     * put it in other words, the hasRight(user, right, ...) call can be
     * performed only either with user == null (only "anybody" rights will
     * apply) or with a user name which is there only after the user was
     * authenticated.
     * 
     * @see org.apache.james.mailbox.acl.MailboxACLResolver#listRightsDefault(boolean)
     */
    @Override
    public MailboxACLRights[] listRights(MailboxACLEntryKey queryKey, GroupMembershipResolver groupMembershipResolver, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        MailboxACL.MailboxACLRights[] positiveNegativePair = { SimpleMailboxACL.NO_RIGHTS, SimpleMailboxACL.NO_RIGHTS };

        MailboxACL userACL = resourceOwnerIsGroup ? groupGlobalACL : userGlobalACL;
        resolveRights(queryKey, groupMembershipResolver, userACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);

        if (queryKey.isNegative()) {
            return toListRightsArray(positiveNegativePair[NEGATIVE_INDEX]);
        } else {
            return toListRightsArray(positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]));
        }
    }

    private static MailboxACLRights[] toListRightsArray(MailboxACLRights implicitRights) throws UnsupportedRightException {
        List<MailboxACLRights> result = new ArrayList<>();
        result.add(implicitRights);
        for (MailboxACLRight right : SimpleMailboxACL.FULL_RIGHTS) {
            if (!implicitRights.contains(right)) {
                result.add(new Rfc4314Rights(right));
            }
        }
        return result.toArray(new MailboxACLRights[result.size()]);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxACLResolver#rightsOf(java.
     *      lang.String, org.apache.james.mailbox.store.mail.MailboxACLResolver.
     *      GroupMembershipResolver, org.apache.james.mailbox.MailboxACL,
     *      java.lang.String)
     */
    @Override
    public MailboxACL.MailboxACLRights resolveRights(String requestUser, GroupMembershipResolver groupMembershipResolver, MailboxACL resourceACL, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        MailboxACL.MailboxACLRights[] positiveNegativePair = { SimpleMailboxACL.NO_RIGHTS, SimpleMailboxACL.NO_RIGHTS };
        final MailboxACLEntryKey queryKey = requestUser == null ? null : new SimpleMailboxACLEntryKey(requestUser, NameType.user, false);
        MailboxACL userACL = resourceOwnerIsGroup ? groupGlobalACL : userGlobalACL;
        resolveRights(queryKey, groupMembershipResolver, userACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);

        if (resourceACL != null) {
            resolveRights(queryKey, groupMembershipResolver, resourceACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);
        }

        return positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]);
    }

    /**
     * What needs to be done for both global ACL and the given mailboxe's ACL.
     * 
     * @param requestUser
     * @param groupMembershipResolver
     * @param entries
     * @param resourceOwner
     * @param resourceOwnerIsGroup
     * @param positiveNegativePair
     * @throws UnsupportedRightException
     */
    private void resolveRights(MailboxACLEntryKey queryKey, GroupMembershipResolver groupMembershipResolver, Map<MailboxACLEntryKey, MailboxACLRights> entries, String resourceOwner, boolean resourceOwnerIsGroup, MailboxACL.MailboxACLRights[] positiveNegativePair)
            throws UnsupportedRightException {
        if (entries != null) {
            for (Entry<MailboxACLEntryKey, MailboxACLRights> entry : entries.entrySet()) {
                final MailboxACLEntryKey key = entry.getKey();
                if (applies(key, queryKey, groupMembershipResolver, resourceOwner, resourceOwnerIsGroup)) {
                    if (key.isNegative()) {
                        positiveNegativePair[NEGATIVE_INDEX] = positiveNegativePair[NEGATIVE_INDEX].union(entry.getValue());
                    } else {
                        positiveNegativePair[POSITIVE_INDEX] = positiveNegativePair[POSITIVE_INDEX].union(entry.getValue());
                    }
                }
            }
        }
    }

}
