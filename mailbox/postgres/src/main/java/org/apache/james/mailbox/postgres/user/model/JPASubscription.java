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
package org.apache.james.mailbox.postgres.user.model;

import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.james.core.Username;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * A subscription to a mailbox by a user.
 */
@Entity(name = "Subscription")
@Table(
    name = "JAMES_SUBSCRIPTION",
    uniqueConstraints = 
        @UniqueConstraint(
                columnNames = {
                        "USER_NAME", 
                        "MAILBOX_NAME"})
)
@NamedQueries({
    @NamedQuery(name = JPASubscription.FIND_MAILBOX_SUBSCRIPTION_FOR_USER,
        query = "SELECT subscription FROM Subscription subscription WHERE subscription.username = :userParam AND subscription.mailbox = :mailboxParam"),          
    @NamedQuery(name = JPASubscription.FIND_SUBSCRIPTIONS_FOR_USER,
        query = "SELECT subscription FROM Subscription subscription WHERE subscription.username = :userParam"),
    @NamedQuery(name = JPASubscription.DELETE_SUBSCRIPTION,
        query = "DELETE subscription FROM Subscription subscription WHERE subscription.username = :userParam AND subscription.mailbox = :mailboxParam")
})
public class JPASubscription {
    public static final String DELETE_SUBSCRIPTION = "deleteSubscription";
    public static final String FIND_SUBSCRIPTIONS_FOR_USER = "findSubscriptionsForUser";
    public static final String FIND_MAILBOX_SUBSCRIPTION_FOR_USER = "findFindMailboxSubscriptionForUser";

    private static final String TO_STRING_SEPARATOR = "  ";

    /** Primary key */
    @GeneratedValue
    @Id 
    @Column(name = "SUBSCRIPTION_ID")
    private long id;
    
    /** Name of the subscribed user */
    @Basic(optional = false)
    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String username;
    
    /** Subscribed mailbox */
    @Basic(optional = false) 
    @Column(name = "MAILBOX_NAME", nullable = false, length = 100)
    private String mailbox;
    
    /**
     * Used by JPA
     */
    @Deprecated
    public JPASubscription() {

    }
    
    /**
     * Constructs a user subscription.
     */
    public JPASubscription(Subscription subscription) {
        super();
        this.username = subscription.getUser().asString();
        this.mailbox = subscription.getMailbox();
    }

    public String getMailbox() {
        return mailbox;
    }

    public Username getUser() {
        return Username.of(username);
    }

    public Subscription toSubscription() {
        return new Subscription(Username.of(username), mailbox);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JPASubscription) {
            JPASubscription that = (JPASubscription) o;

            return Objects.equals(this.id, that.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Renders output suitable for debugging.
     *
     * @return output suitable for debugging
     */
    public String toString() {
        return "Subscription ( "
            + "id = " + this.id + TO_STRING_SEPARATOR
            + "user = " + this.username + TO_STRING_SEPARATOR
            + "mailbox = " + this.mailbox + TO_STRING_SEPARATOR
            + " )";
    }
    
}
