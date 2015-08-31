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

package org.apache.james.utils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.adapter.mailbox.MailboxManagerResolver;
import org.apache.james.adapter.mailbox.MailboxManagerResolverException;
import org.apache.james.mailbox.MailboxManager;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

public class GuiceMailboxManagerResolver implements MailboxManagerResolver {

    private ImmutableMap<String, MailboxManager> managers;

    @Inject
    private GuiceMailboxManagerResolver(Set<MailboxManagerDefinition> managers) {
        this.managers = indexManagersByName(managers);
    }

    private static ImmutableMap<String, MailboxManager> indexManagersByName(Set<MailboxManagerDefinition> managers) {
        return ImmutableMap.copyOf(managers.stream().collect(
                Collectors.toMap(MailboxManagerDefinition::getName, MailboxManagerDefinition::getManager)));
    }

    @Override
    public Map<String, MailboxManager> getMailboxManagerBeans() {
        return managers;
    }

    @Override
    public MailboxManager resolveMailboxManager(String mailboxManagerClassName) {
        return Optional.ofNullable(managers.get(mailboxManagerClassName)).orElseThrow(
                () -> new MailboxManagerResolverException("Unable to find a mailbox manager with name " + mailboxManagerClassName));
    }
    
}
