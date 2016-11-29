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
package org.apache.james.mailbox.store.mail.model.impl;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class SimpleMailbox implements Mailbox {

    private MailboxId id = null;
    private String namespace;
    private String user;
    private String name;
    private final long uidValidity;
    private MailboxACL acl = SimpleMailboxACL.EMPTY;

    public SimpleMailbox(MailboxPath path, long uidValidity, MailboxId mailboxId) {
        this.id = mailboxId;
        this.namespace = path.getNamespace();
        this.user = path.getUser();
        this.name = path.getName();
        this.uidValidity = uidValidity;
    }

    public SimpleMailbox(MailboxPath path, long uidValidity) {
        this(path, uidValidity, null);
    }

    public SimpleMailbox(Mailbox mailbox) {
        this.id = mailbox.getMailboxId();
        this.namespace = mailbox.getNamespace();
        this.user = mailbox.getUser();
        this.name = mailbox.getName();
        this.uidValidity = mailbox.getUidValidity();
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getMailboxId()
     */
    public MailboxId getMailboxId() {
        return id;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getNamespace()
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setNamespace(java.lang.String)
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getUser()
     */
    public String getUser() {
        return user;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setUser(java.lang.String)
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getName()
     */
    public String getName() {
        return name;
    }

    /**
     * @see
     * org.apache.james.mailbox.store.mail.model.Mailbox#setName(java.lang.String
     * )
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getUidValidity()
     */
    public long getUidValidity() {
        return uidValidity;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof SimpleMailbox) {
            if (id != null) {
                if (id.equals(((SimpleMailbox) obj).getMailboxId()))
                    return true;
            } else {
                if (((SimpleMailbox) obj).getMailboxId() == null)
                    return true;
            }
        }
        return false;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + namespace.hashCode();
        if (user != null) {
            result = PRIME * result + user.hashCode();
        }
        result = PRIME * result + name.hashCode();
        return result;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return namespace + ":" + user + ":" + name;
    }

    @Override
    public void setMailboxId(MailboxId id) {
        this.id = id;
    }

    /* (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getACL()
     */
    @Override
    public MailboxACL getACL() {
        return acl;
    }

    /* (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setACL(org.apache.james.mailbox.MailboxACL)
     */
    @Override
    public void setACL(MailboxACL acl) {
        this.acl = acl;
    }

}
