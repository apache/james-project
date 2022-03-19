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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;

import com.github.fge.lambdas.Throwing;

/**
 * This is the Or CompositeMatcher - consider it to be a union of the
 * results.
 *
 * It returns recipients from the Or composition results of the child matchers.
 */
public class Or extends GenericCompositeMatcher {

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return getMatchers().stream()
            .flatMap(Throwing.<Matcher, Stream<MailAddress>>function(matcher -> applyMatcher(matcher, mail))
                .sneakyThrow())
            .collect(Collectors.toSet());
    }

    private Stream<MailAddress> applyMatcher(Matcher matcher, Mail mail) throws MessagingException {
        return Optional.ofNullable(matcher.match(mail))
            .map(Collection::stream)
            .orElse(Stream.of());
    }

}
