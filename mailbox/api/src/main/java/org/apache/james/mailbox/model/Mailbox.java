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

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxUtil;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Models long term mailbox data.
 */
public class Mailbox {
    private final MailboxId id;
    private String namespace;
    private Username user;
    private String name;
    private final long uidValidity;
    private MailboxACL acl = MailboxACL.EMPTY;

    public Mailbox(MailboxPath path, long uidValidity, MailboxId mailboxId) {
        this.id = mailboxId;
        this.namespace = path.getNamespace();
        this.user = path.getUser();
        this.name = path.getName();
        this.uidValidity = uidValidity;
    }

    public Mailbox(Mailbox mailbox) {
        this.id = mailbox.getMailboxId();
        this.namespace = mailbox.getNamespace();
        this.user = mailbox.getUser();
        this.name = mailbox.getName();
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
        return namespace;
    }

    /**
     * Sets the current namespace for this mailbox.
     * @param namespace not null
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the current user for this mailbox.
     * @return not null
     */
    public Username getUser() {
        return user;
    }

    /**
     * Sets the current user for this mailbox.
     * @param user not null
     */
    public void setUser(Username user) {
        this.user = user;
    }

    /**
     * Gets the current name for this mailbox.
     * @return not null
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the current name for this mailbox.
     * @param name not null
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the current UID VALIDITY for this mailbox.
     * @return uid validity
     */
    public long getUidValidity() {
        return uidValidity;
    }

    public MailboxPath generateAssociatedPath() {
        return new MailboxPath(getNamespace(), getUser(), getName());
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
            return Objects.equal(id, o.getMailboxId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, user, name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("user", user)
            .add("name", name)
            .toString();
    }
}