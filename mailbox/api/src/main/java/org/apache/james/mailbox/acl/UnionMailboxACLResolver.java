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

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxACL.SpecialName;
import org.apache.james.mime4j.dom.address.Mailbox;

import com.google.common.collect.ImmutableList;


/**
 * An implementation which works with the union of the rights granted to the
 * applicable identifiers. Inspired by RFC 4314 Section 2.
 * 
 * In
 * {@link MailboxACLResolver#resolveRights(Username, MailboxACL, Username)}
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
    /**
     * Nothing else than full rights for the owner.
     */
    public static final MailboxACL DEFAULT_GLOBAL_USER_ACL = MailboxACL.OWNER_FULL_ACL;

    private static final int POSITIVE_INDEX = 0;
    private static final int NEGATIVE_INDEX = 1;

    /**
     * Stores global ACL which is merged with ACL of every mailbox when
     * computing
     * {@link #rightsOf(String, Mailbox)}
     * and
     * {@link #hasRight(String, Mailbox, Right)}
     * .
     */
    private final MailboxACL userGlobalACL;

    /**
     * Creates a new instance of UnionMailboxACLResolver with
     * {@link #DEFAULT_GLOBAL_USER_ACL} as {@link #userGlobalACL}
     */
    public UnionMailboxACLResolver() {
        super();
        this.userGlobalACL = DEFAULT_GLOBAL_USER_ACL;
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
    }

    /**
     * Tells whether the given {@code aclKey} {@link EntryKey} is
     * applicable for the given {@code queryKey}.
     * 
     * There are two use cases for which this method was designed and tested:
     * 
     * (1) Calls from
     * {@link #hasRight(String, Right, MailboxACL, String)}
     * and
     * {@link MailboxACLResolver#resolveRights(Username, MailboxACL, Username)}
     * in which the {@code queryKey} is a {@link NameType#user}.
     * 
     * (2) Calls from
     * {@link MailboxACLResolver#listRights(EntryKey, Username)}
     * where {@code queryKey} can be anything including {@link NameType#user},
     * {@link NameType#group} and all {@link NameType#special} identifiers.
     * 
     * Clearly the set of cases which this method has to handle in (1) is a
     * proper subset of the cases handled in (2). See the javadoc on
     * {@link MailboxACLResolver#listRights(EntryKey, Username)}
     * for more details.
     */
    protected static boolean applies(EntryKey aclKey, EntryKey queryKey, Username resourceOwner) {
        final String aclKeyName = aclKey.getName();
        final NameType aclKeyNameType = aclKey.getNameType();
        if (SpecialName.anyone.name().equals(aclKeyName)) {
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
                        /* non-null query user is viewed as authenticated */
                        return true;
                    } else if (SpecialName.owner.name().equals(aclKeyName)) {
                        return queryUserOrGroupName.equals(resourceOwner.asString());
                    } else {
                        /* should not happen unless the parent if is changed */
                        throw new IllegalStateException("Unexpected " + SpecialName.class.getName() + "." + aclKeyName);
                    }
                case user:
                    return aclKeyName.equals(queryUserOrGroupName);
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
                        return false;
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
                    /*
                     * query owner matches authenticated because owner will
                     * be resolved only if the user is authenticated
                     */
                    if (aclKeyName.equals(queryUserOrGroupName)) {
                        /*
                         * authenticated matches authenticated and owner matches
                         * owner
                         */
                        return true;
                    } else {
                        /*
                         * query owner matches authenticated because owner will
                         * be resolved only if the user is authenticated
                         */
                        return SpecialName.owner.name().equals(queryUserOrGroupName) && SpecialName.authenticated.name().equals(aclKeyName);
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
            /* non-anyone ACL keys do not match non-authenticated queries */
            return false;
        }
    }

    @Override
    public MailboxACL applyGlobalACL(MailboxACL resourceACL) throws UnsupportedRightException {
        return resourceACL.union(userGlobalACL);
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
     * <li>the "anyone" entry</li>
     * </ul>
     * 
     * (2) if {@code queryKey} is a group key, the rights included come from the
     * following ACL entries:
     * <ul>
     * <li>the entry literally matching the given group name</li>
     * <li>if the given group is the owner of the given mailbox also the "owner"
     * entry is included</li>
     * <li>the "authenticated" entry (*)</li>
     * <li>the "anyone" entry</li>
     * </ul>
     * 
     * (3) if {@code queryKey} is a special key, the rights included come from
     * the following ACL entries:
     * <ul>
     * <li>the entry literally matching the given special name</li>
     * <li>the "authenticated" entry if the {@code queryKey} is the "owner"
     * query key (*)</li>
     * <li>the "anyone" entry</li>
     * </ul>
     * 
     * (*) This is the most questionable case: should "authenticated" ACL
     * entries hold for group name queries? We say yes. Firstly, listing
     * implicit rights for, say "group1", should inform which rights do not need
     * to be set explicitly for the members of "group1". And secondly the group
     * rights are actually queried and applied only for authenticated users. To
     * put it in other words, the hasRight(user, right, ...) call can be
     * performed only either with user == null (only "anyone" rights will
     * apply) or with a user name which is there only after the user was
     * authenticated.
     */
    @Override
    public List<Rfc4314Rights> listRights(EntryKey queryKey, Username resourceOwner) throws UnsupportedRightException {
        Rfc4314Rights[] positiveNegativePair = { MailboxACL.NO_RIGHTS, MailboxACL.NO_RIGHTS };

        resolveRights(queryKey, userGlobalACL.getEntries(), resourceOwner, positiveNegativePair);

        if (queryKey.isNegative()) {
            return toListRights(positiveNegativePair[NEGATIVE_INDEX]);
        } else {
            return toListRights(positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]));
        }
    }

    private static List<Rfc4314Rights> toListRights(Rfc4314Rights implicitRights) throws UnsupportedRightException {
        return Stream.concat(
            MailboxACL.FULL_RIGHTS
                .list()
                .stream()
                .filter(not(implicitRights::contains))
                .map(Rfc4314Rights::new),
            Stream.of(implicitRights))
        .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Rfc4314Rights resolveRights(Username requestUser, MailboxACL resourceACL, Username resourceOwner) throws UnsupportedRightException {
        Rfc4314Rights[] positiveNegativePair = { MailboxACL.NO_RIGHTS, MailboxACL.NO_RIGHTS };
        final EntryKey queryKey = requestUser == null ? null : EntryKey.createUserEntryKey(requestUser);
        resolveRights(queryKey, userGlobalACL.getEntries(), resourceOwner, positiveNegativePair);

        if (resourceACL != null) {
            resolveRights(queryKey, resourceACL.getEntries(), resourceOwner, positiveNegativePair);
        }

        return positiveNegativePair[POSITIVE_INDEX].except(positiveNegativePair[NEGATIVE_INDEX]);
    }

    /**
     * What needs to be done for both global ACL and the given mailboxe's ACL.
     */
    private void resolveRights(EntryKey queryKey, Map<EntryKey, Rfc4314Rights> entries, Username resourceOwner, Rfc4314Rights[] positiveNegativePair)
            throws UnsupportedRightException {
        if (entries != null) {
            for (Entry<EntryKey, Rfc4314Rights> entry : entries.entrySet()) {
                final EntryKey key = entry.getKey();
                if (applies(key, queryKey, resourceOwner)) {
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
