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

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * Implements the interpretation of ACLs.
 * 
 * From RFC4314: <cite>It is possible for multiple identifiers in an access
 * control list to apply to a given user. For example, an ACL may include rights
 * to be granted to the identifier matching the user, one or more
 * implementation-defined identifiers matching groups that include the user,
 * and/or the identifier "anyone". How these rights are combined to determine
 * the users access is implementation defined. An implementation may choose, for
 * example, to use the union of the rights granted to the applicable
 * identifiers. An implementation may instead choose, for example, to use only
 * those rights granted to the most specific identifier present in the ACL. A
 * client can determine the set of rights granted to the logged-in user for a
 * given mailbox name by using the MYRIGHTS command. </cite>
 * 
 */
public interface MailboxACLResolver {

    /**
     * Applies global ACL to the given <code>resourceACL</code>. From RFC 4314:
     * An implementation [...] MAY force rights to always or never be granted to
     * particular identifiers.
     */
    MailboxACL applyGlobalACL(MailboxACL resourceACL, boolean resourceOwnerIsGroup) throws UnsupportedRightException;

    /**
     * Computes a result suitable for the LISTRIGHTS IMAP command. The result is
     * computed regardless of mailbox. Therefore it should be viewed as a
     * general default which may be further customised depending on the given
     * mailbox.
     * 
     * @param key
     *            the identifier from the LISTRIGHTS command
     * @param resourceOwner
     *            the owner of the mailbox named in the LISTRIGHTS command. User
     *            name or group name.
     * @param resourceOwnerIsGroup
     *            true if the {@code resourceOwner} is a group of users, false
     *            otherwise.
     * @return an array of {@link MailboxACL.Rfc4314Rights}. The first element is the
     *         set of implicit (global) rights which does not need to be set
     *         explicitly for the given identifier. Further elements are groups
     *         of rights which can be set for the given identifier and resource.
     */
    List<MailboxACL.Rfc4314Rights> listRights(MailboxACL.EntryKey key, GroupMembershipResolver groupMembershipResolver, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException;

    /**
     * Computes the rights which apply to the given user and resource. Global
     * ACL (if there is any) should be applied within this method.
     * 
     * @param requestUser
     *            the user for whom the rights are computed, possibly
     *            <code>null</code> when there is no authenticated user in the
     *            given context.
     * @param groupMembershipResolver
     *            this resolver is used when checking whether any group rights
     *            contained in resourceACL are applicable for the requestUser.
     * @param resourceACL
     *            the ACL defining the access right for the resource in
     *            question.
     * @param resourceOwner
     *            this user name is used as a replacement for the "owner" place
     *            holder in the resourceACL.
     * @param resourceOwnerIsGroup
     *            true if the resourceOwner is a group of users, false
     *            otherwise.
     * @return the rights applicable for the given user and resource.
     */
    MailboxACL.Rfc4314Rights resolveRights(Username requestUser, GroupMembershipResolver groupMembershipResolver, MailboxACL resourceACL, String resourceOwner, boolean resourceOwnerIsGroup) throws UnsupportedRightException;

}
