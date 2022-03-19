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

package org.apache.james.mailetcontainer.impl.matchers;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * This is the Xor CompositeMatcher - consider it to be the inequality
 * operator for recipients. If any recipients match other matcher results
 * then the result does not include that recipient.
 *
 * It returns Recipients from the Xor composition of the child matchers.
 */
public class Xor extends GenericCompositeMatcher {

    @Override
    public Collection<MailAddress> match(Mail mail) {
        Set<MailAddress> recipients = getMatchers().stream()
            .map(Throwing.<Matcher, Set<MailAddress>>function(matcher -> applyMatcher(mail, matcher)).sneakyThrow())
            .reduce(ImmutableSet.of(), this::performXor);

        if (recipients.isEmpty()) {
            return null;
        }
        return recipients;
    }

    private Set<MailAddress> applyMatcher(Mail mail, Matcher matcher) throws MessagingException {
        return Optional.ofNullable(matcher.match(mail))
            .map(ImmutableSet::copyOf)
            .orElse(ImmutableSet.of());
    }

    private Set<MailAddress> performXor(Set<MailAddress> set1, Set<MailAddress> set2) {
        return Sets.difference(
            Sets.union(set1, set2),
            Sets.intersection(set1, set2));
    }

}
