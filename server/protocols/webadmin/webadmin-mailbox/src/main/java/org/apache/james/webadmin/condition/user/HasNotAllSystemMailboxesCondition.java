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

package org.apache.james.webadmin.condition.user;

import static org.apache.james.mailbox.DefaultMailboxes.DEFAULT_MAILBOXES;

import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.UserCondition;
import org.apache.james.webadmin.dto.MailboxResponse;
import org.apache.james.webadmin.service.UserMailboxesService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class HasNotAllSystemMailboxesCondition implements UserCondition {
    public static final String HAS_NOT_ALL_SYSTEM_MAILBOXES_PARAM = "hasNotAllSystemMailboxes";

    private static final Set<String> DEFAULT_MAILBOX_SET = DEFAULT_MAILBOXES.stream().collect(ImmutableSet.toImmutableSet());

    private final UserMailboxesService userMailboxesService;

    @Inject
    public HasNotAllSystemMailboxesCondition(UserMailboxesService userMailboxesService) {
        this.userMailboxesService = userMailboxesService;
    }

    @Override
    public boolean test(Username username) {
        try {
            Set<String> mailboxes = userMailboxesService.listMailboxes(username, UserMailboxesService.Options.Force)
                .stream()
                .map(MailboxResponse::getMailboxName)
                .collect(ImmutableSet.toImmutableSet());
            return !Sets.difference(DEFAULT_MAILBOX_SET, mailboxes).isEmpty();
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }
}
