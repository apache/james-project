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

import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;


/**
 * Expresses select criteria for mailboxes.
 */
public class MailboxQuery {

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

        public Builder user(Username username) {
            this.username(username.asString());
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

    public static class UserBound extends MailboxQuery {
        private UserBound(Optional<String> namespace, Optional<String> user, MailboxNameExpression mailboxNameExpression) {
            super(namespace, user, mailboxNameExpression);
            Preconditions.checkArgument(namespace.isPresent());
            Preconditions.checkArgument(user.isPresent());
        }

        public String getFixedNamespace() {
            return namespace.get();
        }

        public String getFixedUser() {
            return user.get();
        }
    }

    protected final Optional<String> namespace;
    protected final Optional<String> user;
    private final MailboxNameExpression mailboxNameExpression;

    /**
     * Constructs an expression determining a set of mailbox names.
     */
    private MailboxQuery(Optional<String> namespace, Optional<String> user, MailboxNameExpression mailboxNameExpression) {
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
        Username sessionUsername = session.getUser();
        return namespace.map(MailboxConstants.USER_NAMESPACE::equals).orElse(false)
            && user.map(Username::of).map(sessionUsername::equals).orElse(false);
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

    public UserBound asUserBound() {
        return new UserBound(namespace, user, mailboxNameExpression);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxQuery) {
            MailboxQuery that = (MailboxQuery) o;

            return Objects.equals(this.namespace, that.namespace)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.mailboxNameExpression, that.mailboxNameExpression);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, user, mailboxNameExpression);
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("user", user)
            .add("mailboxNameExpression", mailboxNameExpression)
            .toString();
    }

}
