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
package org.apache.james.mailbox;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class Role {

    public static final String USER_DEFINED_ROLE_PREFIX = "x-";

    private static final BiFunction<String, String, Boolean> CASE_SENSITIVE_COMPARATOR = (a, b) -> a.equals(b);
    private static final BiFunction<String, String, Boolean> NON_CASE_SENSITIVE_COMPARATOR = (a, b) -> a.equalsIgnoreCase(b);

    public static final Role INBOX = new Role("inbox", DefaultMailboxes.INBOX, NON_CASE_SENSITIVE_COMPARATOR);
    public static final Role DRAFTS = new Role("drafts", DefaultMailboxes.DRAFTS, CASE_SENSITIVE_COMPARATOR);
    public static final Role OUTBOX = new Role("outbox", DefaultMailboxes.OUTBOX, CASE_SENSITIVE_COMPARATOR);
    public static final Role SENT = new Role("sent", DefaultMailboxes.SENT, CASE_SENSITIVE_COMPARATOR);
    public static final Role TRASH = new Role("trash", DefaultMailboxes.TRASH, CASE_SENSITIVE_COMPARATOR);
    public static final Role ARCHIVE = new Role("archive", DefaultMailboxes.ARCHIVE, CASE_SENSITIVE_COMPARATOR);
    public static final Role SPAM = new Role("spam", DefaultMailboxes.SPAM, CASE_SENSITIVE_COMPARATOR);
    public static final Role TEMPLATES = new Role("templates", DefaultMailboxes.TEMPLATES, CASE_SENSITIVE_COMPARATOR);
    
    private static final List<Role> ROLES = 
            ImmutableList.<Role>of(INBOX, DRAFTS, OUTBOX, SENT, TRASH, ARCHIVE, SPAM, TEMPLATES);
    
    private final String name;
    private final String defaultMailbox;
    private final BiFunction<String, String, Boolean> comparator;

    @VisibleForTesting Role(String name, String defaultMailbox, BiFunction<String, String, Boolean> comparator) {
        this.name = name;
        this.defaultMailbox = defaultMailbox;
        this.comparator = comparator;
    }

    @VisibleForTesting Role(String name) {
        this.name = name;
        this.defaultMailbox = null;
        this.comparator = NON_CASE_SENSITIVE_COMPARATOR;
    }

    public static Optional<Role> from(String name) {
        Optional<Role> predefinedRole = predefinedRole(name);
        if (predefinedRole.isPresent()) {
            return predefinedRole;
        } else {
            return tryBuildCustomRole(name);
        }
    }

    private static Optional<Role> predefinedRole(String name) {
        return ROLES.stream()
                .filter(role -> role.comparator.apply(role.defaultMailbox, name))
                .findFirst();
    }

    private static Optional<Role> tryBuildCustomRole(String name) {
        if (name.startsWith(USER_DEFINED_ROLE_PREFIX)) {
            return Optional.of(new Role(name));
        }
        return Optional.empty();
    }

    public boolean isSystemRole() {
        return predefinedRole(defaultMailbox).isPresent();
    }

    public String serialize() {
        return name;
    }

    public String getDefaultMailbox() {
        return defaultMailbox;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, defaultMailbox);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Role) {
            Role that = (Role) object;
            return Objects.equal(this.name, that.name)
                && Objects.equal(this.defaultMailbox, that.defaultMailbox);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("defaultMailbox", defaultMailbox)
            .toString();
    }
}
