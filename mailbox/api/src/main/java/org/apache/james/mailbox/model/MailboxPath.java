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

import org.apache.james.mailbox.MailboxSession;

/**
 * The path to a mailbox.
 */
public class MailboxPath {

    private String namespace;
    private String user;
    private String name;


    
    public MailboxPath(String namespace, String user, String name) {
        if (namespace == null || namespace.equals("")) {
            this.namespace = MailboxConstants.USER_NAMESPACE;
        } else {
            this.namespace = namespace;
        }
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
     * Set the namespace this mailbox is in
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
     * Set the name of the user who owns the mailbox.
     */
    public void setUser(String user) {
        this.user = user;
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

    /**
     * Set the name of the mailbox. This is the pure name without user or
     * namespace, so this is what a user would see in his client.
     */
    public void setName(String name) {
        this.name = name;
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
        ArrayList<MailboxPath> levels = new ArrayList<MailboxPath>();
        int index = name.indexOf(delimiter);
        while (index >= 0) {
            final String levelname = name.substring(0, index);
            levels.add(new MailboxPath(namespace, user, levelname));
            index = name.indexOf(delimiter, ++index);
        }
        levels.add(this);
        return levels;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return namespace + ":" + user + ":" + name;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object mailboxPath) {
        if (this == mailboxPath)
            return true;

        if (!(mailboxPath instanceof MailboxPath))
            return false;
        MailboxPath mp = (MailboxPath) mailboxPath;
        if (namespace == null) {
            if (mp.getNamespace() != null)
                return false;
        } else if (!namespace.equals(mp.getNamespace()))
            return false;
        if (user == null) {
            if (mp.getUser() != null)
                return false;
        } else if (!user.equals(mp.getUser()))
            return false;
        if (name == null) {
            if (mp.getName() != null)
                return false;
        } else if (!name.equals(mp.getName()))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        if (getName() != null)
            result = PRIME * result + getName().hashCode();
        if (getUser() != null)
            result = PRIME * result + getUser().hashCode();
        if (getNamespace() != null)
            result = PRIME * result + getNamespace().hashCode();
        return result;
    }
    
    /**
     * Return the full name of the {@link MailboxPath}, which is constructed via the {@link #namespace} and {@link #name}
     * 
     * @param delimiter
     * @return fullName
     */
    public String getFullName(char delimiter) {
        return namespace + delimiter + name;
    }

    /**
     * Return a {@link MailboxPath} which represent the INBOX of the given
     * session
     * 
     * @param session
     * @return inbox
     */
    public static MailboxPath inbox(MailboxSession session) {
        return new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), MailboxConstants.INBOX);
    }
    
    /**
     * Create a {@link MailboxPath} by parsing the given full mailboxname (which included the namespace)
     * 
     * @param session
     * @param fullmailboxname
     * @return path
     */
    public static MailboxPath parse(MailboxSession session, String fullmailboxname) {
        char delimiter = session.getPathDelimiter();
        int i = fullmailboxname.indexOf(delimiter);
        String namespace = fullmailboxname.substring(0, i);
        String mailbox = fullmailboxname.substring(i + 1, fullmailboxname.length());
        String username = null;
        if (namespace == null || namespace.trim().equals("")) {
            namespace = MailboxConstants.USER_NAMESPACE;
        } 
        if (namespace.equals(session.getPersonalSpace())) {
            username = session.getUser().getUserName();
        }
        return new MailboxPath(namespace, username, mailbox);

    }

}
