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


package org.apache.james.transport.mailets;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * This mailet removes all of the headers starting with a given prefix in the message (global) and per recipient (specific).
 *
 * Sample configuration:
 *
 * <pre><code>
 * &lt;mailet match="All" class="RemoveMimeHeaderByPrefix"&gt;
 *   &lt;name&gt;X-APPLICATIVE-HEADER-&lt;/name&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */

public class RemoveMimeHeaderByPrefix extends GenericMailet {

    public static final String PREFIX = "prefix";

    private String prefix;

    @Override
    public void init() throws MessagingException {
        prefix = getInitParameter(PREFIX);
        if (Strings.isNullOrEmpty(prefix)) {
            throw new MessagingException("Expecting prefix not to be empty or null with RemoveMimeHeaderByPrefix");
        }
    }

    @Override
    public String getMailetInfo() {
        return "RemoveMimeHeaderByPrefix Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        List<String> headerNamesToRemove = headerNamesStartingByPrefix(mail);
        for (String headerName: headerNamesToRemove) {
            mail.getMessage().removeHeader(headerName);
        }
        if (!headerNamesToRemove.isEmpty()) {
            mail.getMessage().saveChanges();
        }

        removeSpecific(mail);
    }

    protected void removeSpecific(Mail mail) {
        mail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders()
                .stream()
                .collect(Collectors.toList()) // Streaming for concurrent modifications
                .forEach(recipient -> 
                    mail.getPerRecipientSpecificHeaders()
                        .getHeadersForRecipient(recipient)
                        .removeIf(next -> next.getName().startsWith(prefix)));
    }

    private List<String> headerNamesStartingByPrefix(Mail mail) throws MessagingException {
        ImmutableList.Builder<String> headerToRemove = ImmutableList.builder();
        List<Header> headers = new MimeMessageUtils(mail.getMessage()).toHeaderList();
        for (Header header: headers) {
            if (header.getName().startsWith(prefix)) {
                headerToRemove.add(header.getName());
            }
        }
        return headerToRemove.build();
    }
}

