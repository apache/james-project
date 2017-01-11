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

package org.apache.james.transport.mailets.redirect;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class NotifyMailetsMessage {

    private static final char LINE_BREAK = '\n';

    public String generateMessage(String parameterMessage, Mail originalMail) throws MessagingException {
        MimeMessage message = originalMail.getMessage();
        StringBuilder builder = new StringBuilder();

        builder.append(parameterMessage).append(LINE_BREAK);
        if (originalMail.getErrorMessage() != null) {
            builder.append(LINE_BREAK)
                .append("Error message below:")
                .append(LINE_BREAK)
                .append(originalMail.getErrorMessage())
                .append(LINE_BREAK);
        }
        builder.append(LINE_BREAK)
            .append("Message details:")
            .append(LINE_BREAK);

        if (message.getSubject() != null) {
            builder.append("  Subject: " + message.getSubject())
                .append(LINE_BREAK);
        }
        if (message.getSentDate() != null) {
            builder.append("  Sent date: " + message.getSentDate())
                .append(LINE_BREAK);
        }
        builder.append("  MAIL FROM: " + originalMail.getSender())
            .append(LINE_BREAK);

        boolean firstRecipient = true;
        for (MailAddress recipient : originalMail.getRecipients()) {
            if (firstRecipient) {
                builder.append("  RCPT TO: " + recipient)
                    .append(LINE_BREAK);
                firstRecipient = false;
            } else {
                builder.append("           " + recipient)
                    .append(LINE_BREAK);
            }
        }

        appendAddresses(builder, "From", message.getHeader(RFC2822Headers.FROM));
        appendAddresses(builder, "To", message.getHeader(RFC2822Headers.TO));
        appendAddresses(builder, "CC", message.getHeader(RFC2822Headers.CC));

        builder.append("  Size (in bytes): " + message.getSize())
            .append(LINE_BREAK);
        if (message.getLineCount() >= 0) {
            builder.append("  Number of lines: " + message.getLineCount())
                .append(LINE_BREAK);
        }

        return builder.toString();
    }

    private void appendAddresses(StringBuilder builder, String title, String[] addresses) {
        if (addresses != null) {
            builder.append("  " + title + ": ")
                .append(LINE_BREAK);
            for (String address : flatten(addresses)) {
                builder.append(address + " ")
                    .append(LINE_BREAK);
            }
            builder.append(LINE_BREAK);
        }
    }

    private List<String> flatten(String[] addresses) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String address : addresses) {
            builder.addAll(Splitter.on(',').trimResults().split(address));
        }
        return builder.build();
    }
}
