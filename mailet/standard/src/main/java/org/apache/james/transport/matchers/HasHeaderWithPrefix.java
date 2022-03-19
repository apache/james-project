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
import java.util.List;
import java.util.Map;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * Matches emails with headers having a given prefix.
 *
 * If a header with the given prefix is found in the message (global) all recipients will be matched. If a header with the given prefix is found per recipient (specific), only these will be matched. 
 * Otherwise, no recipient in returned.
 *
 * use: <pre><code>&lt;mailet match="HasHeaderWithPrefix=PREFIX" class="..." /&gt;</code></pre>
 */
public class HasHeaderWithPrefix extends GenericMatcher {

    private String prefix;

    @Override
    public void init() throws MessagingException {
        prefix = getCondition();
        if (Strings.isNullOrEmpty(prefix)) {
            throw new MessagingException("Expecting prefix not to be empty or null with RemoveMimeHeaderByPrefix");
        }
    }

    @Override
    public String getMatcherInfo() {
        return "HasHeaderWithPrefix Matcher";
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        List<Header> headers = new MimeMessageUtils(mail.getMessage()).toHeaderList();

        for (Header header: headers) {
            if (header.getName().startsWith(prefix)) {
                return mail.getRecipients();
            }
        }

        return matchSpecific(mail);
    }

    protected Collection<MailAddress> matchSpecific(Mail mail) {
        return mail.getPerRecipientSpecificHeaders().getHeadersByRecipient()
                .entries()
                .stream()
                .filter(entry -> entry.getValue().getName().startsWith(prefix))
                .map(Map.Entry::getKey)
                .collect(ImmutableSet.toImmutableSet());
    }
}
