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

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxACL.SpecialName;
import org.apache.james.mime4j.dom.address.Mailbox;


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
    public static final MailboxACL DEFAULT_GLOBAL_GROUP_ACL = MailboxACL.OWNER_FULL_EXCEPT_ADMINISTRATION_ACL;

    /**
     * Nothing else than full rights for the owner.
     */
    public static final MailboxACL DEFAULT_GLOBAL_USER_ACL = MailboxACL.OWNER_FULL_ACL;

    private static final int POSITIVE_INDEX = 0;
    private static final int NEGATIVE_INDEX = 1;

    private final MailboxACL groupGlobalACL;
    /**
     * Stores global ACL which is merged with ACL of every mailbox when
     * computing
     * {@link #rightsOf(String, org.apache.james.mailbox.MailboxACLResolver.GroupMembershipResolver, Mailbox)}
     * and
     * {@link #hasRight(String, Mailbox, Right, org.apache.james.mailbox.MailboxACLResolver.GroupMembershipResolver)}
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
     * @param userGlobalACL
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
     * Tells whether the given {@code aclKey} {@link EntryKey} is
     * applicable for the given {@code queryKey}.
     * 
     * There are two use cases for which this method was designed and tested:
     * 
     * (1) Calls from
     * {@link #hasRight(String, GroupMembershipResolver, Right, MailboxACL, String, boolean)}
     * and
     * {@link #resolveRights(String, GroupMembershipResolver, MailboxACL, String, boolean)}
     * in which the {@code queryKey} is a {@link NameType#user}.
     * 
     * (2) Calls from
     * {@link #listRights(EntryKey, GroupMembershipResolver, String, boolean)}
     * where {@code queryKey} can be anything including {@link NameType#user},
     * {@link NameType#group} and all {@link NameType#special} identifiers.
     * 
     * Clearly the set of cases which this method has to handle in (1) is a
     * proper subset of the cases handled in (2). See the javadoc on
     * {@link #listRights(EntryKey, GroupMembershipResolver, String, boolean)}
     * for more details.
     */
    protected static boolean applies(EntryKey aclKey, EntryKey queryKey, GroupMembershipResolver groupMembershipResolver, String resourceOwner, boolean resourceOwnerIsGroup) {
        final String aclKeyName = aclKey.getName();
        final NameType aclKeyNameType = aclKey.getNameType();
        if (SpecialName.anybody.name().equals(aclKeyName)) {
            /* this works also for unauthenticated users */
            return true;
        } else if (queryKey != null) {
            String queryUserOrGroupName = queryKey.getName();
            switch (queryKey.getNameType()) {
            case user:
                /* Authenticated users */
                switch (aclKeyNameType) {
                case special:
                    if (SpecialName.authenticated.name().equals(aclKeyName)) {
                        /* non null query user is viewed as authenticated */
                        return true;
                    } else if (SpecialName.owner.name().equals(aclKeyName)) {
                        return (!resourceOwnerIsGroup && queryUserOrGroupName.equals(resourceOwner)) || (resourceOwnerIsGroup && groupMembershipResolver.isMember(Username.of(queryUserOrGroupName), resourceOwner));
                    } else {
                        /* should not happen unless the parent if is changed */
                        throw new IllegalStateException("Unexpected " + SpecialName.class.getName() + "." + aclKeyName);
                    }
                case user:
                    return aclKeyName.equals(queryUserOrGroupName);
                case group:
                    return groupMembershipResolver.isMember(Username.of(queryUserOrGroupName), aclKeyName);
                default:
                    throw new IllegalStateException("Unexpected " + NameType.class.getName() + "." + aclKeyNameType);
                }
            case group:
                /* query is a group */
                switch (aclKeyNameType) {
                case special:
                    if (SpecialName.authenticated.name().equals(aclKeyName)) {
                        /*
                         * see the javadoc comment on listRights()
                         */
                        return true;
                    } else if (SpecialName.owner.name().equals(aclKeyName)) {
                        return resourceOwnerIsGroup && queryUserOrGroupName.equals(resourceOwner);
                    } else {
                        /* should not happen unless the parent if is changed */
                        throw new IllegalStateException("Unexpected " + SpecialName.class.getName() + "." + aclKeyName);
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
                    } else if (SpecialName.owner.name().equals(queryUserOrGroupName) && SpecialName.authenticated.name().equals(aclKeyName)) {
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

    @Override
    public MailboxACL applyGlobalACL(MailboxACL resourceACL, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        return resourceOwnerIsGroup ? resourceACL.union(groupGlobalACL) : resourceACL.union(userGlobalACL);
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
     */
    @Override
    public Rfc4314Rights[] listRights(EntryKey queryKey, GroupMembershipResolver groupMembershipResolver, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        Rfc4314Rights[] positiveNegativePair = { MailboxACL.NO_RIGHTS, MailboxACL.NO_RIGHTS };

        MailboxACL userACL = resourceOwnerIsGroup ? groupGlobalACL : userGlobalACL;
        resolveRights(queryKey, groupMembershipResolver, userACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);

        if (queryKey.isNegative()) {
            return toListRightsArray(positiveNegativePair[NEGATIVE_INDEX]);
        } else {
            return toListRightsArray(positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]));
        }
    }

    private static Rfc4314Rights[] toListRightsArray(Rfc4314Rights implicitRights) throws UnsupportedRightException {
        List<Rfc4314Rights> result = new ArrayList<>();
        result.add(implicitRights);
        for (Right right : MailboxACL.FULL_RIGHTS.list()) {
            if (!implicitRights.contains(right)) {
                result.add(new Rfc4314Rights(right));
            }
        }
        return result.toArray(Rfc4314Rights[]::new);
    }

    @Override
    public Rfc4314Rights resolveRights(Username requestUser, GroupMembershipResolver groupMembershipResolver, MailboxACL resourceACL, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException {
        Rfc4314Rights[] positiveNegativePair = { MailboxACL.NO_RIGHTS, MailboxACL.NO_RIGHTS };
        final EntryKey queryKey = requestUser == null ? null : EntryKey.createUserEntryKey(requestUser);
        MailboxACL userACL = resourceOwnerIsGroup ? groupGlobalACL : userGlobalACL;
        resolveRights(queryKey, groupMembershipResolver, userACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);

        if (resourceACL != null) {
            resolveRights(queryKey, groupMembershipResolver, resourceACL.getEntries(), resourceOwner, resourceOwnerIsGroup, positiveNegativePair);
        }

        return positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]);
    }

    /**
     * What needs to be done for both global ACL and the given mailboxe's ACL.
     */
    private void resolveRights(EntryKey queryKey, GroupMembershipResolver groupMembershipResolver, Map<EntryKey, Rfc4314Rights> entries, String resourceOwner, boolean resourceOwnerIsGroup, Rfc4314Rights[] positiveNegativePair)
            throws UnsupportedRightException {
        if (entries != null) {
            for (Entry<EntryKey, Rfc4314Rights> entry : entries.entrySet()) {
                final EntryKey key = entry.getKey();
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
