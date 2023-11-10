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

import java.util.Objects;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxUtil;

import com.google.common.base.MoreObjects;

/**
 * Models long term mailbox data.
 */
public class Mailbox {
    private final MailboxId id;
    private MailboxPath path;
    private final UidValidity uidValidity;
    private MailboxACL acl = MailboxACL.EMPTY;

    public Mailbox(MailboxPath path, UidValidity uidValidity, MailboxId mailboxId) {
        this.id = mailboxId;
        this.path = path;
        this.uidValidity = uidValidity;
    }

    public Mailbox(Mailbox mailbox) {
        this.id = mailbox.getMailboxId();
        this.path = mailbox.generateAssociatedPath();
        this.uidValidity = mailbox.getUidValidity();
        this.acl = new MailboxACL(mailbox.getACL().getEntries());
    }

    /**
     * Gets the unique mailbox ID.
     * @return mailbox id
     */
    public MailboxId getMailboxId() {
        return id;
    }

    /**
     * Gets the current namespace for this mailbox.
     * @return not null
     */
    public String getNamespace() {
        return path.getNamespace();
    }

    /**
     * Sets the current namespace for this mailbox.
     * @param namespace not null
     */
    public void setNamespace(String namespace) {
        this.path = new MailboxPath(namespace, path.getUser(), path.getName());
    }

    /**
     * Gets the current user for this mailbox.
     * @return not null
     */
    public Username getUser() {
        return path.getUser();
    }

    /**
     * Sets the current user for this mailbox.
     * @param user not null
     */
    public void setUser(Username user) {
        this.path = new MailboxPath(path.getNamespace(), user, path.getName());
    }

    /**
     * Gets the current name for this mailbox.
     * @return not null
     */
    public String getName() {
        return path.getName();
    }

    /**
     * Sets the current name for this mailbox.
     * @param name not null
     */
    public void setName(String name) {
        this.path = new MailboxPath(path.getNamespace(), path.getUser(), name);
    }

    /**
     * Gets the current UID VALIDITY for this mailbox.
     * @return uid validity
     */
    public UidValidity getUidValidity() {
        return uidValidity;
    }

    public MailboxPath generateAssociatedPath() {
        return path;
    }

    /**
     * Gets the current ACL for this mailbox.
     */
    public MailboxACL getACL() {
        return acl;
    }

    /**
     * Sets the current ACL for this mailbox.
     */
    public void setACL(MailboxACL acl) {
        this.acl = acl;
    }

    public boolean isChildOf(Mailbox potentialParent, MailboxSession mailboxSession) {
        return MailboxUtil.isMailboxChildOf(this, potentialParent, mailboxSession);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Mailbox) {
            Mailbox o = (Mailbox)obj;
            return Objects.equals(id, o.getMailboxId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", getMailboxId().serialize())
            .add("namespace", path.getNamespace())
            .add("user", path.getUser())
            .add("name", path.getName())
            .toString();
    }
}