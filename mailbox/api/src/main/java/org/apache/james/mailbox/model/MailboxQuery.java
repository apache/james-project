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

import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.james.mailbox.MailboxSession;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;


/**
 * Expresses select criteria for mailboxes.
 */
public final class MailboxQuery {
    public static final char SQL_WILDCARD_CHAR = '%';
    
    /**
     * Use this wildcard to match every char including the hierarchy delimiter
     */
    public final static char FREEWILDCARD = '*';

    /**
     * Use this wildcard to match every char except the hierarchy delimiter
     */
    public final static char LOCALWILDCARD = '%';
    private static final String EMPTY_PATH_NAME = "";

    private final MailboxPath base;
    private final String expression;
    private final char pathDelimiter;
    private final Pattern pattern;
    private final Optional<String> namespace;
    private final Optional<String> user;
    private final Optional<String> name;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder privateMailboxesBuilder(MailboxSession session) {
        return builder()
            .mailboxSession(session)
            .username(session.getUser().getUserName());
    }

    public static class Builder {
        private String expression;
        @VisibleForTesting MailboxSession mailboxSession;
        @VisibleForTesting Optional<String> username;
        @VisibleForTesting Optional<String> pathName;
        @VisibleForTesting Optional<String> namespace;
        
        private Builder() {
            this.pathName = Optional.empty();
            this.namespace = Optional.empty();
            this.username = Optional.empty();
        }
        
        public Builder base(MailboxPath base) {
            this.namespace = Optional.ofNullable(base.getNamespace());
            this.username = Optional.ofNullable(base.getUser());
            this.pathName = Optional.ofNullable(base.getName());
            return this;
        }
        
        public Builder username(String username) {
            this.username = Optional.of(username);
            return this;
        }
        
        public Builder privateMailboxes() {
            Preconditions.checkState(!pathName.isPresent());
            Preconditions.checkState(!namespace.isPresent());
            this.namespace = Optional.of(MailboxConstants.USER_NAMESPACE);
            this.pathName = Optional.of(EMPTY_PATH_NAME);
            return matchesAll();
        }
        
        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }
        
        public Builder matchesAll() {
            this.expression = String.valueOf(FREEWILDCARD);
            return this;
        }
        
        public Builder mailboxSession(MailboxSession session) {
            this.mailboxSession = session;
            return this;
        }
        
        public MailboxQuery build() {
            Preconditions.checkState(mailboxSession != null);
            return new MailboxQuery(namespace, username, pathName, expression, mailboxSession);
        }
    }

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
    @VisibleForTesting MailboxQuery(Optional<String> namespace, Optional<String> user, Optional<String> name,
                                    String expression, MailboxSession session) {
        this.namespace = namespace;
        this.user = user;
        this.name = name;
        this.base = new MailboxPath(
            namespace.orElse(MailboxConstants.USER_NAMESPACE),
            user.orElse(session.getUser().getUserName()),
            name.orElse(EMPTY_PATH_NAME));
        if (expression == null) {
            this.expression = "";
        } else {
            this.expression = expression;
        }
        this.pathDelimiter = session.getPathDelimiter();
        pattern = constructEscapedRegex();
    }

    public MailboxPath getPathLike() {
        String combinedName = getCombinedName()
            .replace(getFreeWildcard(), SQL_WILDCARD_CHAR)
            .replace(getLocalWildcard(), SQL_WILDCARD_CHAR)
            + SQL_WILDCARD_CHAR;
        return new MailboxPath(getBase(), combinedName);
    }

    public boolean belongsToRequestedNamespaceAndUser(MailboxPath mailboxPath) {
        boolean belongsToRequestedNamespace = namespace
            .map(value -> value.equals(mailboxPath.getNamespace()))
            .orElse(true);
        boolean belongsToRequestedUser = user
            .map(value -> value.equals(mailboxPath.getUser()))
            .orElse(true);

        return belongsToRequestedNamespace && belongsToRequestedUser;
    }

    /**
     * Gets the base reference for the search.
     * 
     * @return the base
     */
    public final MailboxPath getBase() {
        return base;
    }

    /**
     * Gets the name search expression. This may contain wildcards.
     * 
     * @return the expression
     */
    public final String getExpression() {
        return expression;
    }

    /**
     * Gets wildcard character that matches any series of characters.
     * 
     * @return the freeWildcard
     */
    public final char getFreeWildcard() {
        return FREEWILDCARD;
    }

    /**
     * Gets wildcard character that matches any series of characters excluding
     * hierarchy delimiters. Effectively, this means that it matches any
     * sequence within a name part.
     * 
     * @return the localWildcard
     */
    public final char getLocalWildcard() {
        return LOCALWILDCARD;
    }

    /**
     * Is the given name a match for {@link #getExpression()}?
     * 
     * @param name
     *            name to be matched
     * @return true if the given name matches this expression, false otherwise
     */
    public boolean isExpressionMatch(String name) {
        final boolean result;
        if (isWild()) {
            if (name == null) {
                result = false;
            } else {
                result = pattern.matcher(name).matches();
            }
        } else {
            result = expression.equals(name);
        }
        return result;
    }

    public boolean isPathMatch(MailboxPath mailboxPath) {
        String baseName = name.orElse(EMPTY_PATH_NAME);
        int baseNameLength = baseName.length();
        String mailboxName = mailboxPath.getName();

        return belongsToRequestedNamespaceAndUser(mailboxPath)
            && mailboxName.startsWith(baseName)
            && isExpressionMatch(mailboxName.substring(baseNameLength));
    }
  
    /**
     * Get combined name formed by adding the expression to the base using the
     * given hierarchy delimiter. Note that the wildcards are retained in the
     * combined name.
     * 
     * @return {@link #getBase()} combined with {@link #getExpression()},
     *         notnull
     */
    public String getCombinedName() {
        final String result;
        if (base != null && base.getName() != null && base.getName().length() > 0) {
            final int baseLength = base.getName().length();
            if (base.getName().charAt(baseLength - 1) == pathDelimiter) {
                if (expression != null && expression.length() > 0) {
                    if (expression.charAt(0) == pathDelimiter) {
                        result = base.getName() + expression.substring(1);
                    } else {
                        result = base.getName() + expression;
                    }
                } else {
                    result = base.getName();
                }
            } else {
                if (expression != null && expression.length() > 0) {
                    if (expression.charAt(0) == pathDelimiter) {
                        result = base.getName() + expression;
                    } else {
                        result = base.getName() + pathDelimiter + expression;
                    }
                } else {
                    result = base.getName();
                }
            }
        } else {
            result = expression;
        }
        return result;
    }

    /**
     * Is this expression wild?
     * 
     * @return true if wildcard contained, false otherwise
     */
    public boolean isWild() {
        return expression != null && (expression.indexOf(getFreeWildcard()) >= 0 || expression.indexOf(getLocalWildcard()) >= 0);
    }

    /**
     * Renders a string suitable for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";
        return "MailboxExpression [ " + "base = " + this.base + TAB + "expression = " + this.expression + TAB + "freeWildcard = " + this.getFreeWildcard() + TAB + "localWildcard = " + this.getLocalWildcard() + TAB + " ]";
    }


    private Pattern constructEscapedRegex() {
        StringBuilder stringBuilder = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(expression, "*%", true);
        while (tokenizer.hasMoreTokens()) {
            stringBuilder.append(getRegexPartAssociatedWithToken(tokenizer));
        }
        return Pattern.compile(stringBuilder.toString());
    }

    private String getRegexPartAssociatedWithToken(StringTokenizer tokenizer) {
        String token = tokenizer.nextToken();
        if (token.equals("*")) {
            return ".*";
        } else if (token.equals("%")) {
            return "[^" + Pattern.quote(String.valueOf(pathDelimiter)) + "]*";
        } else {
            return Pattern.quote(token);
        }
    }

}
