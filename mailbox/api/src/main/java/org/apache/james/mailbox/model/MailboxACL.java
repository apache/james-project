/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.model;

import java.util.Map;

import org.apache.james.mailbox.exception.UnsupportedRightException;

/**
 * Stores an Access Control List (ACL) applicable to a mailbox. Inspired by
 * RFC4314 IMAP4 Access Control List (ACL) Extension.
 * 
 * Implementations must be immutable. Implementations should override
 * {@link #hashCode()} and {@link #equals(Object)}.
 * 
 */
public interface MailboxACL {

    /**
     * SETACL command mode.
     */
    enum EditMode {
        ADD, REMOVE, REPLACE
    }

    /**
     * The key used in {@link MailboxACL#getEntries()}. Implementations should
     * override {@link #hashCode()} and {@link #equals(Object)} in such a way
     * that all of {@link #getName()}, {@link #getNameType()} and
     * {@link #isNegative()} are significant.
     * 
     */
    public interface MailboxACLEntryKey {
        /**
         * Returns the name of a user or of a group to which this
         * {@link MailboxACLEntryKey} applies.
         * 
         * @return User name, group name or special name.
         */
        String getName();

        /**
         * Tells of what type is the name returned by {@link #getName()}.
         * 
         * @return type of the name returned by {@link #getName()}
         */
        NameType getNameType();

        /**
         * If true the {@link MailboxACLRights} returned by
         * {@link MailboxACLEntry#getRights()} should be interpreted as
         * "negative rights" as described in RFC4314: If the identifier "-fred"
         * is granted the "w" right, that indicates that the "w" right is to be
         * removed from users matching the identifier "fred", even though the
         * user "fred" might have the "w" right as a consequence of some other
         * identifier in the ACL.
         * 
         * Note that {@link MailboxACLEntry#getName()} does not start with "-"
         * when {@link MailboxACLEntry#getRights()} returns true.
         * 
         * @return
         */
        boolean isNegative();

        /**
         * Returns a serialized form of this {@link MailboxACLEntryKey} as a
         * {@link String}. Implementations should choose a consistent way how
         * all of {@link #getName()}, {@link #getNameType()} and
         * {@link #isNegative()} get serialized.
         * 
         * RFC4314 sction 2. states: All user name strings accepted by the LOGIN
         * or AUTHENTICATE commands to authenticate to the IMAP server are
         * reserved as identifiers for the corresponding users. Identifiers
         * starting with a dash ("-") are reserved for "negative rights",
         * described below. All other identifier strings are interpreted in an
         * implementation-defined manner.
         * 
         * Dovecot and Cyrus mark groups with '$' prefix. See <a
         * href="http://wiki2.dovecot.org/SharedMailboxes/Shared"
         * >http://wiki2.dovecot.org/SharedMailboxes/Shared</a>:
         * 
         * <cite>The $group syntax is not a standard, but it is mentioned in RFC
         * 4314 examples and is also understood by at least Cyrus IMAP. Having
         * '-' before the identifier specifies negative rights.</cite>
         * 
         * @see MailboxACL#DEFAULT_GROUP_MARKER
         * @see MailboxACL#DEFAULT_NEGATIVE_MARKER
         * 
         * @return serialized form as a {@link String}
         */
        String serialize();
    }

    /**
     * Single right applicable to a mailbox.
     */
    public interface MailboxACLRight {
        /**
         * Returns the char representation of this right.
         * 
         * @return char representation of this right
         */
        char getValue();
    }

    /**
     * Iterable set of {@link MailboxACLRight}s.
     * 
     * Implementations may decide to support only a specific range of rights,
     * e.g. the Standard Rights of RFC 4314 section 2.1.
     * 
     * Implementations must not allow adding or removing of elements once this
     * MailboxACLRights are initialized.
     */
    public interface MailboxACLRights extends Iterable<MailboxACLRight> {

        /**
         * Tells whether this contains the given right.
         * 
         * @param right
         * @return
         * @throws UnsupportedRightException
         *             iff the given right is not supported.
         */
        boolean contains(MailboxACLRight right) throws UnsupportedRightException;

        /**
         * Performs the set theoretic operation of relative complement of
         * toRemove MailboxACLRights in this MailboxACLRights.
         * 
         * A schematic example: "lrw".except("w") returns "lr".
         * 
         * Implementations must return a new unmodifiable instance of
         * {@link MailboxACLRights}. However, implementations may decide to
         * return this or toRemove parameter value in case the result would be
         * equal to the respective one of those.
         * 
         * @param toRemove
         * @return
         * @throws UnsupportedRightException
         */
        public MailboxACLRights except(MailboxACLRights toRemove) throws UnsupportedRightException;

        /**
         * Tells if this set of rights is empty.
         * 
         * @return true if there are no rights in this set; false otherwise.
         */
        public boolean isEmpty();

        /**
         * Tells whether the implementation supports the given right.
         * 
         * @param right
         * @return true if this supports the given right.
         */
        boolean isSupported(MailboxACLRight right);

        /**
         * Returns a serialized form of this {@link MailboxACLRights} as
         * {@link String}.
         * 
         * @return a {@link String}
         */
        String serialize();

        /**
         * Performs the set theoretic operation of union of this
         * MailboxACLRights and toAdd MailboxACLRights.
         * 
         * A schematic example: "lr".union("rw") returns "lrw".
         * 
         * Implementations must return a new unmodifiable instance of
         * {@link MailboxACLRights}. However, implementations may decide to
         * return this or toAdd parameter value in case the result would be
         * equal to the respective one of those.
         * 
         * @param toAdd
         * @return union of this and toAdd
         * @throws UnsupportedRightException
         * 
         */
        public abstract MailboxACLRights union(MailboxACLRights toAdd) throws UnsupportedRightException;

    };

    /**
     * Allows distinguishing between users, groups and special names (see
     * {@link SpecialName}).
     */

    public interface MailboxACLCommand {
        MailboxACLEntryKey getEntryKey();

        EditMode getEditMode();

        MailboxACLRights getRights();
    };

    enum NameType {
        group, special, user
    };

    /**
     * Special name literals.
     */
    enum SpecialName {
        anybody, authenticated, owner
    };

    /**
     * SETACL third argument prefix
     */
    public static final char ADD_RIGHTS_MARKER = '+';

    /**
     * Marks groups when (de)serializing {@link MailboxACLEntryKey}s.
     * 
     * @see MailboxACLEntryKey#serialize()
     */
    public static final char DEFAULT_GROUP_MARKER = '$';

    /**
     * Marks negative when (de)serializing {@link MailboxACLEntryKey}s.
     * 
     * @see MailboxACLEntryKey#serialize()
     */
    public static final char DEFAULT_NEGATIVE_MARKER = '-';

    /**
     * SETACL third argument prefix
     */
    public static final char REMOVE_RIGHTS_MARKER = '-';

    /**
     * Apply the given ACL update on current ACL and return the result as a new ACL.
     *
     * @param aclUpdate Update to perform
     * @return Copy of current ACL updated
     * @throws UnsupportedRightException
     */
    MailboxACL apply(MailboxACLCommand aclUpdate) throws UnsupportedRightException;

    /**
     * Performs the set theoretic operation of relative complement of toRemove
     * {@link MailboxACL} in this {@link MailboxACL}.
     * 
     * A schematic example: "user1:lr;user2:lrwt".except("user1:w;user2:t")
     * returns "user1:lr;user2:lrw".
     * 
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this or
     * toRemove parameter value in case the result would be equal to the
     * respective one of those.
     * 
     * Implementations must ensure that the result does not contain entries with
     * empty rigths. E.g. "user1:lr;user2:lrwt".except("user1:lr") should return
     * "user2:lrwt" rather than "user1:;user2:lrwt"
     * 
     * @param toRemove
     * @return
     * @throws UnsupportedRightException
     */
    MailboxACL except(MailboxACL toRemove) throws UnsupportedRightException;

    /**
     * TODO except.
     * 
     * @param key
     * @param toRemove
     * @return
     * @throws UnsupportedRightException
     */
    MailboxACL except(MailboxACLEntryKey key, MailboxACLRights toRemove) throws UnsupportedRightException;

    /**
     * {@link Map} of entries.
     * 
     * @return the entries.
     */
    Map<MailboxACLEntryKey, MailboxACLRights> getEntries();

    /**
     * Replaces the entry corresponding to the given {@code key} with
     * {@code toAdd}link MailboxACLRights}.
     * 
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this in
     * case the result would be equal to it.
     * 
     * Implementations must ensure that the result does not contain entries with
     * empty rigths. E.g. "user1:lr;user2:lrwt".replace("user1",
     * MailboxACLRights.EMPTY) should return "user2:lrwt" rather than
     * "user1:;user2:lrwt". The same result should be returned by
     * "user1:lr;user2:lrwt".replace("user1", null).
     * 
     * @param key
     * @param toAdd
     * @return
     * @throws UnsupportedRightException
     */
    MailboxACL replace(MailboxACLEntryKey key, MailboxACLRights toAdd) throws UnsupportedRightException;

    /**
     * Performs the set theoretic operation of union of this {@link MailboxACL}
     * and toAdd {@link MailboxACL}.
     * 
     * A schematic example:
     * "user1:lr;user2:lrwt".union("user1:at;-$group1:lrwt") returns
     * "user1:alrt;user2:lrwt;-$group1:lrwt".
     * 
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this or
     * toAdd parameter value in case the result would be equal to the respective
     * one of those.
     * 
     * 
     * @param toAdd
     * @return
     * @throws UnsupportedRightException
     */
    MailboxACL union(MailboxACL toAdd) throws UnsupportedRightException;

    /**
     * TODO union.
     * 
     * @param key
     * @param toAdd
     * @return
     * @throws UnsupportedRightException
     */
    MailboxACL union(MailboxACLEntryKey key, MailboxACLRights toAdd) throws UnsupportedRightException;

}
