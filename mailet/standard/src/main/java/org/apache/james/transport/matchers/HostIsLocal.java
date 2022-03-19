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



package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

/**
 * Matches mail to Domains which are local
 * .
 */
public class HostIsLocal extends GenericMatcher {
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return recipientsByDomains(mail)
            .flatMap(this::hasLocalDomain)
            .collect(ImmutableList.toImmutableList());
    }

    private Stream<MailAddress> hasLocalDomain(Map.Entry<Domain, Collection<MailAddress>> entry) {
        if (getMailetContext().isLocalServer(entry.getKey())) {
            return entry.getValue().stream();
        }
        return Stream.empty();
    }

    private Stream<Map.Entry<Domain, Collection<MailAddress>>> recipientsByDomains(Mail mail) {
        return mail.getRecipients()
            .stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(MailAddress::getDomain, Function.identity()))
            .asMap()
            .entrySet()
            .stream();
    }
}
