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

package org.apache.james.mailbox.model.search;

import java.util.Optional;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;


/**
 * Expresses select criteria for mailboxes.
 */
public final class MailboxQuery {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder privateMailboxesBuilder(MailboxSession session) {
        return builder()
            .namespace(MailboxConstants.USER_NAMESPACE)
            .username(session.getUser().asString())
            .matchesAllMailboxNames();
    }

    public static class Builder {
        private static final Wildcard DEFAULT_WILDCARD = Wildcard.INSTANCE;

        Optional<String> username;
        Optional<String> namespace;
        Optional<MailboxNameExpression> mailboxNameExpression;
        
        private Builder() {
            this.namespace = Optional.empty();
            this.username = Optional.empty();
            this.mailboxNameExpression = Optional.empty();
        }
        
        public Builder userAndNamespaceFrom(MailboxPath base) {
            Preconditions.checkState(!this.namespace.isPresent());
            Preconditions.checkState(!this.username.isPresent());

            this.namespace = Optional.ofNullable(base.getNamespace());
            this.username = Optional.ofNullable(base.getUser());
            return this;
        }

        public Builder username(String username) {
            Preconditions.checkState(!this.username.isPresent());

            this.username = Optional.of(username);
            return this;
        }

        public Builder namespace(String namespace) {
            Preconditions.checkState(!this.namespace.isPresent());

            this.namespace = Optional.of(namespace);
            return this;
        }

        public Builder privateNamespace() {
            Preconditions.checkState(!this.namespace.isPresent());

            this.namespace = Optional.of(MailboxConstants.USER_NAMESPACE);
            return this;
        }
        
        public Builder expression(MailboxNameExpression expression) {
            this.mailboxNameExpression = Optional.of(expression);
            return this;
        }
        
        public Builder matchesAllMailboxNames() {
            this.mailboxNameExpression = Optional.of(Wildcard.INSTANCE);
            return this;
        }
        
        public MailboxQuery build() {
            return new MailboxQuery(namespace, username, mailboxNameExpression.orElse(DEFAULT_WILDCARD));
        }
    }

    private final Optional<String> namespace;
    private final Optional<String> user;
    private final MailboxNameExpression mailboxNameExpression;

    /**
     * Constructs an expression determining a set of mailbox names.
     * 
     * @param base
     *            base reference name, not null
     * @param expression
     *            mailbox match expression, not null
     * @param pathDelimiter
     *            path delimiter to use
     */
    @VisibleForTesting MailboxQuery(Optional<String> namespace, Optional<String> user, MailboxNameExpression mailboxNameExpression) {
        this.namespace = namespace;
        this.user = user;
        this.mailboxNameExpression = mailboxNameExpression;
    }

    public Optional<String> getNamespace() {
        return namespace;
    }

    public Optional<String> getUser() {
        return user;
    }

    public MailboxNameExpression getMailboxNameExpression() {
        return mailboxNameExpression;
    }

    public boolean isPrivateMailboxes(MailboxSession session) {
        User sessionUser = session.getUser();
        return namespace.map(MailboxConstants.USER_NAMESPACE::equals).orElse(false)
            && user.map(User::fromUsername).map(sessionUser::equals).orElse(false);
    }

    @VisibleForTesting
    boolean belongsToRequestedNamespaceAndUser(MailboxPath mailboxPath) {
        boolean belongsToRequestedNamespace = namespace
            .map(value -> value.equals(mailboxPath.getNamespace()))
            .orElse(true);
        boolean belongsToRequestedUser = user
            .map(value -> value.equals(mailboxPath.getUser()))
            .orElse(true);

        return belongsToRequestedNamespace && belongsToRequestedUser;
    }

    public boolean isExpressionMatch(String name) {
        return mailboxNameExpression.isExpressionMatch(name);
    }

    public boolean isPathMatch(MailboxPath mailboxPath) {
        return belongsToRequestedNamespaceAndUser(mailboxPath)
            && isExpressionMatch(mailboxPath.getName());
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("user", user)
            .add("mailboxNameExpression", mailboxNameExpression)
            .toString();
    }

}
