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

import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.collect.ImmutableList;

/**
 * The path to a mailbox.
 */
public class MailboxPath {
    /**
     * Return a {@link MailboxPath} which represent the INBOX of the given
     * session
     *
     * @param session
     * @return inbox
     */
    public static MailboxPath inbox(MailboxSession session) {
        return MailboxPath.forUser(session.getUser().asString(), MailboxConstants.INBOX);
    }

    /**
     * Create a {@link MailboxPath} in the prive namespace of the specified user
     */
    public static MailboxPath forUser(String username, String mailboxName) {
        return new MailboxPath(MailboxConstants.USER_NAMESPACE, username, mailboxName);
    }

    private final String namespace;
    private final String user;
    private final String name;
    
    public MailboxPath(String namespace, String user, String name) {
        this.namespace = Optional.ofNullable(namespace)
            .filter(s -> !s.isEmpty())
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
     * 
     * @return The namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the name of the user who owns the mailbox. This can be null e.g. for
     * shared mailboxes.
     * 
     * @return The username
     */
    public String getUser() {
        return user;
    }

    /**
     * Get the name of the mailbox. This is the pure name without user or
     * namespace, so this is what a user would see in his client.
     * 
     * @return The name string
     */
    public String getName() {
        return name;
    }

    public boolean belongsTo(MailboxSession mailboxSession) {
        return user.equalsIgnoreCase(mailboxSession.getUser().asString());
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
     * @param delimiter
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


    public String asString() {
        return namespace + ":" + user + ":" + name;
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
     * @param delimiter
     * @return fullName
     * @deprecated Use {@link MailboxPath#asString()} instead.
     */
    @Deprecated
    public String getFullName(char delimiter) {
        return namespace + delimiter + name;
    }

}
