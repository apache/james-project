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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.HasEmptyMailboxNameInHierarchyException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * The path to a mailbox.
 */
public class MailboxPath {
    /**
     * Return a {@link MailboxPath} which represent the INBOX of the given
     * session
     */
    public static MailboxPath inbox(MailboxSession session) {
        return MailboxPath.inbox(session.getUser());
    }

    public static MailboxPath inbox(Username username) {
        return MailboxPath.forUser(username, MailboxConstants.INBOX);
    }

    /**
     * Create a {@link MailboxPath} in the prive namespace of the specified user
     */
    public static MailboxPath forUser(Username username, String mailboxName) {
        return new MailboxPath(MailboxConstants.USER_NAMESPACE, username, mailboxName);
    }

    private static final String INVALID_CHARS = "%*&#";
    private static final CharMatcher INVALID_CHARS_MATCHER = CharMatcher.anyOf(INVALID_CHARS);
    // This is the size that all mailbox backend should support
    public  static final int MAX_MAILBOX_NAME_LENGTH = 200;

    private final String namespace;
    private final Username user;
    private final String name;
    
    public MailboxPath(String namespace, Username user, String name) {
        this.namespace = Optional.ofNullable(namespace)
            .filter(Predicate.not(String::isEmpty))
            .orElse(MailboxConstants.USER_NAMESPACE);
        this.user = user;
        this.name = name;
    }

    public MailboxPath(MailboxPath mailboxPath) {
        this(mailboxPath.getNamespace(), mailboxPath.getUser(), mailboxPath.getName());
    }

    public MailboxPath(MailboxPath mailboxPath, String name) {
        this(mailboxPath.getNamespace(), mailboxPath.getUser(), name);
    }

    /**
     * Get the namespace this mailbox is in
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the name of the user who owns the mailbox.
     */
    public Username getUser() {
        return user;
    }

    /**
     * Get the name of the mailbox. This is the pure name without user or
     * namespace, so this is what a user would see in his client.
     */
    public String getName() {
        return name;
    }

    public boolean belongsTo(MailboxSession mailboxSession) {
        return user.equals(mailboxSession.getUser());
    }

    public MailboxPath child(String childName, char delimiter) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(childName), "'childName' should not be null or empty");
        Preconditions.checkArgument(!childName.contains(String.valueOf(delimiter)), "'childName' should not contain delimiter");

        return new MailboxPath(namespace, user, name + delimiter + childName);
    }

    /**
     * Return a list of MailboxPath representing the hierarchy levels of this
     * MailboxPath. E.g. INBOX.main.sub would yield
     * 
     * <pre>
     * INBOX
     * INBOX.main
     * INBOX.main.sub
     * </pre>
     *
     * @return list of hierarchy levels
     */
    public List<MailboxPath> getHierarchyLevels(char delimiter) {
        if (name == null) {
            return ImmutableList.of(this);
        }
        ArrayList<MailboxPath> levels = new ArrayList<>();
        int index = name.indexOf(delimiter);
        while (index >= 0) {
            final String levelname = name.substring(0, index);
            levels.add(new MailboxPath(namespace, user, levelname));
            index = name.indexOf(delimiter, ++index);
        }
        levels.add(this);
        return levels;
    }

    public MailboxPath sanitize(char delimiter) {
        if (name == null) {
            return this;
        }
        if (name.endsWith(String.valueOf(delimiter))) {
            int length = name.length();
            String sanitizedName = name.substring(0, length - 1);
            return new MailboxPath(
                namespace,
                user,
                sanitizedName);
        }
        return this;
    }

    public MailboxPath assertAcceptable(char pathDelimiter) throws MailboxNameException {
        if (hasEmptyNameInHierarchy(pathDelimiter)) {
            throw new HasEmptyMailboxNameInHierarchyException(
                String.format("'%s' has an empty part within its mailbox name considering %s as a delimiter", asString(), pathDelimiter));
        }
        if (nameContainsForbiddenCharacters()) {
            throw new MailboxNameException(asString() + " contains one of the forbidden characters " + INVALID_CHARS);
        }
        if (isMailboxNameTooLong()) {
            throw new TooLongMailboxNameException("Mailbox name exceeds maximum size of " + MAX_MAILBOX_NAME_LENGTH + " characters");
        }
        return this;
    }

    private boolean nameContainsForbiddenCharacters() {
        return INVALID_CHARS_MATCHER.matchesAnyOf(name);
    }

    private boolean isMailboxNameTooLong() {
        return name.length() > MAX_MAILBOX_NAME_LENGTH;
    }

    boolean hasEmptyNameInHierarchy(char pathDelimiter) {
        String delimiterString = String.valueOf(pathDelimiter);
        return this.name.isEmpty()
            || this.name.contains(delimiterString + delimiterString)
            || this.name.startsWith(delimiterString)
            || this.name.endsWith(delimiterString);
    }

    public String asString() {
        return namespace + ":" + user.asString() + ":" + name;
    }

    public boolean isInbox() {
        return DefaultMailboxes.INBOX.equalsIgnoreCase(name);
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxPath) {
            MailboxPath that = (MailboxPath) o;

            return Objects.equals(this.namespace, that.namespace)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.name, that.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, user, name);
    }

    /**
     * Return the full name of the {@link MailboxPath}, which is constructed via the {@link #namespace} and {@link #name}
     *
     * @deprecated Use {@link MailboxPath#asString()} instead.
     */
    @Deprecated
    public String getFullName(char delimiter) {
        return namespace + delimiter + name;
    }

}
