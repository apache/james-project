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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Enable to validate that the sender of the email address domain name has a MX record advertised through DNS
 * that matches a supplied list of domains.
 *
 * This is for instance useful when registering dynamically a public email service and ensure correct configuration
 * of the domain upon sending and mitigate some identity spoofing attempts on a publicly accessible platform allowing
 * dynamic domain registration.
 *
 * Example:
 *
 *  &lt;mailet match=&quot;SenderHasMXRecord=mx.apache.org&quot; class=&quot;&lt;any-class&gt;&quot;/&gt;
 */
public class SenderHasMXRecord extends GenericMatcher {
    private final DNSService dnsService;
    private ImmutableList<String> expectedMxRecords;

    @Inject
    public SenderHasMXRecord(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public void init() throws MessagingException {
        expectedMxRecords = Splitter.on(',')
            .splitToStream(getCondition())
            .map(Domain::of)
            .map(Domain::asString)
            .sorted()
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return mail.getMaybeSender().asOptional()
            .filter(this::matchesExpectedMxRecord)
            .map(any -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }

    private boolean matchesExpectedMxRecord(MailAddress address) {
        try {
            ImmutableList<String> mxRecords = dnsService.findMXRecords(address.getDomain().asString())
                .stream()
                .sorted()
                .distinct()
                .collect(ImmutableList.toImmutableList());

            return expectedMxRecords.equals(mxRecords);
        } catch (TemporaryResolutionException e) {
            return false;
        }
    }
}
