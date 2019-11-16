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
package org.apache.james.mailbox.jpa.mail.model;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.apache.james.core.Username;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

@Entity(name = "Mailbox")
@Table(name = "JAMES_MAILBOX")
@NamedQueries({
    @NamedQuery(name = "findMailboxById",
        query = "SELECT mailbox FROM Mailbox mailbox WHERE mailbox.mailboxId = :idParam"),
    @NamedQuery(name = "findMailboxByName",
        query = "SELECT mailbox FROM Mailbox mailbox WHERE mailbox.name = :nameParam and mailbox.user is NULL and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "findMailboxByNameWithUser",
        query = "SELECT mailbox FROM Mailbox mailbox WHERE mailbox.name = :nameParam and mailbox.user= :userParam and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "findMailboxWithNameLikeWithUser",
        query = "SELECT mailbox FROM Mailbox mailbox WHERE mailbox.name LIKE :nameParam and mailbox.user= :userParam and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "findMailboxWithNameLike",
        query = "SELECT mailbox FROM Mailbox mailbox WHERE mailbox.name LIKE :nameParam and mailbox.user is NULL and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "countMailboxesWithNameLikeWithUser",
        query = "SELECT COUNT(mailbox) FROM Mailbox mailbox WHERE mailbox.name LIKE :nameParam and mailbox.user= :userParam and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "countMailboxesWithNameLike",
        query = "SELECT COUNT(mailbox) FROM Mailbox mailbox WHERE mailbox.name LIKE :nameParam and mailbox.user is NULL and mailbox.namespace= :namespaceParam"),
    @NamedQuery(name = "listMailboxes",
        query = "SELECT mailbox FROM Mailbox mailbox"),
    @NamedQuery(name = "findHighestModSeq",
        query = "SELECT mailbox.highestModSeq FROM Mailbox mailbox WHERE mailbox.mailboxId = :idParam"),
    @NamedQuery(name = "findLastUid",
        query = "SELECT mailbox.lastUid FROM Mailbox mailbox WHERE mailbox.mailboxId = :idParam")
})
public class JPAMailbox {
    
    private static final String TAB = " ";

    public static JPAMailbox from(Mailbox mailbox) {
        return new JPAMailbox(mailbox);
    }

    /** The value for the mailboxId field */
    @Id
    @GeneratedValue
    @Column(name = "MAILBOX_ID")
    private long mailboxId;
    
    /** The value for the name field */
    @Basic(optional = false)
    @Column(name = "MAILBOX_NAME", nullable = false, length = 200)
    private String name;

    /** The value for the uidValidity field */
    @Basic(optional = false)
    @Column(name = "MAILBOX_UID_VALIDITY", nullable = false)
    private long uidValidity;

    @Basic(optional = true)
    @Column(name = "USER_NAME", nullable = true, length = 200)
    private String user;
    
    @Basic(optional = false)
    @Column(name = "MAILBOX_NAMESPACE", nullable = false, length = 200)
    private String namespace;

    @Basic(optional = false)
    @Column(name = "MAILBOX_LAST_UID", nullable = true)
    private long lastUid;
    
    @Basic(optional = false)
    @Column(name = "MAILBOX_HIGHEST_MODSEQ", nullable = true)
    private long highestModSeq;

    /**
     * JPA only
     */
    @Deprecated
    public JPAMailbox() {
    }
    
    public JPAMailbox(MailboxPath path, long uidValidity) {
        this.name = path.getName();
        this.user = path.getUser().asString();
        this.namespace = path.getNamespace();
        this.uidValidity = uidValidity;
    }

    public JPAMailbox(Mailbox mailbox) {
        this(mailbox.generateAssociatedPath(), mailbox.getUidValidity());
    }

    public JPAId getMailboxId() {
        return JPAId.of(mailboxId);
    }

    public void setMailboxId(MailboxId mailboxId) {
        this.mailboxId = ((JPAId)mailboxId).getRawId();
    }

    public long consumeUid() {
        return ++lastUid;
    }

    public long consumeModSeq() {
        return ++highestModSeq;
    }

    public Mailbox toMailbox() {
        return new Mailbox(generateAssociatedPath(), uidValidity, new JPAId(mailboxId));
    }

    public MailboxPath generateAssociatedPath() {
        return new MailboxPath(namespace, Username.of(user), name);
    }

    public void setMailboxId(long mailboxId) {
        this.mailboxId = mailboxId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        return "Mailbox ( "
            + "mailboxId = " + this.mailboxId + TAB
            + "name = " + this.name + TAB
            + "uidValidity = " + this.uidValidity + TAB
            + " )";
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (mailboxId ^ (mailboxId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JPAMailbox other = (JPAMailbox) obj;
        if (mailboxId != other.mailboxId) {
            return false;
        }
        return true;
    }
}
