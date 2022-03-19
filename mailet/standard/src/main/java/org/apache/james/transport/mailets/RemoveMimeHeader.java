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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Remove mime headers from the message (global) and per recipient (specific).
 * 
 * Sample configuration:
 * 
 * <pre><code>
 * &lt;mailet match="All" class="RemoveMimeHeader"&gt;
 * &lt;name&gt;header1,header2&lt;/name&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 * 
 */
public class RemoveMimeHeader extends GenericMailet {
    private List<String> headers;

    @Override
    public void init() throws MailetException {
        String header = getInitParameter("name");
        if (header == null) {
            throw new MailetException("Invalid config. Please specify atleast one name");
        }
        headers = ImmutableList.copyOf(Splitter.on(",").split(header));
    }

    @Override
    public String getMailetInfo() {
        return "RemoveMimeHeader Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        boolean hasHeadersToRemove = message.getMatchingHeaderLines(headers.toArray(String[]::new))
            .hasMoreElements();

        for (String header : headers) {
            message.removeHeader(header);
        }

        removeSpecific(mail);

        if (hasHeadersToRemove) {
            message.saveChanges();
        }
    }

    protected void removeSpecific(Mail mail) {
        // Copying to avoid concurrent modifications
        ImmutableList.copyOf(mail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders())
            .forEach(recipient -> mail.getPerRecipientSpecificHeaders()
                .getHeadersForRecipient(recipient)
                .removeIf(next -> headers.contains(next.getName())));
    }
}
