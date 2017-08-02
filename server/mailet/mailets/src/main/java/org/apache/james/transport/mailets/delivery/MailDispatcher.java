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
package org.apache.james.transport.mailets.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDispatcher.class);
    public static final String[] NO_HEADERS = {};

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public static final boolean CONSUME = true;
        private MailStore mailStore;
        private Optional<Boolean> consume = Optional.absent();
        private MailetContext mailetContext;

        public Builder consume(boolean consume) {
            this.consume = Optional.of(consume);
            return this;
        }

        public Builder mailStore(MailStore mailStore) {
            this.mailStore = mailStore;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public MailDispatcher build() throws MessagingException {
            Preconditions.checkNotNull(mailStore);
            Preconditions.checkNotNull(mailetContext);
            return new MailDispatcher(mailStore, consume.or(CONSUME), mailetContext);
        }

    }

    private final MailStore mailStore;
    private final boolean consume;
    private final MailetContext mailetContext;

    private MailDispatcher(MailStore mailStore, boolean consume, MailetContext mailetContext) {
        this.mailStore = mailStore;
        this.consume = consume;
        this.mailetContext = mailetContext;
    }

    public void dispatch(Mail mail) throws MessagingException {
        Collection<MailAddress> errors =  customizeHeadersAndDeliver(mail);
        if (!errors.isEmpty()) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            mailetContext.sendMail(mail.getSender(), errors, mail.getMessage(), Mail.ERROR);
        }
        if (consume) {
            // Consume this message
            mail.setState(Mail.GHOST);
        }
    }

    private Collection<MailAddress> customizeHeadersAndDeliver(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        // Set Return-Path and remove all other Return-Path headers from the message
        // This only works because there is a placeholder inserted by MimeMessageWrapper
        message.setHeader(RFC2822Headers.RETURN_PATH, DeliveryUtils.prettyPrint(mail.getSender()));

        Collection<MailAddress> errors = deliver(mail, message);

        return errors;
    }

    private Collection<MailAddress> deliver(Mail mail, MimeMessage message) {
        Collection<MailAddress> errors = new Vector<>();
        for (MailAddress recipient : mail.getRecipients()) {
            try {
                Map<String, List<String>> savedHeaders = saveHeaders(mail, recipient);

                addSpecificHeadersForRecipient(mail, message, recipient);
                mailStore.storeMail(recipient, mail);
                
                restoreHeaders(mail.getMessage(), savedHeaders);
            } catch (Exception ex) {
                LOGGER.error("Error while storing mail.", ex);
                errors.add(recipient);
            }
        }
        return errors;
    }

    private Map<String, List<String>> saveHeaders(Mail mail, MailAddress recipient) throws MessagingException {
        ImmutableMap.Builder<String, List<String>> backup = ImmutableMap.builder();
        Collection<String> headersToSave = mail.getPerRecipientSpecificHeaders().getHeaderNamesForRecipient(recipient);
        for (String headerName: headersToSave) {
            List<String> values = ImmutableList.copyOf(
                        Optional.fromNullable(mail.getMessage().getHeader(headerName))
                            .or(NO_HEADERS));
            backup.put(headerName, values);
        }
        return backup.build();
    }

    private void restoreHeaders(MimeMessage mimeMessage, Map<String, List<String>> savedHeaders) throws MessagingException {
        for (Map.Entry<String, List<String>> header: savedHeaders.entrySet()) {
            String name = header.getKey();
            mimeMessage.removeHeader(name);
            for (String value: header.getValue()) {
                mimeMessage.addHeader(name, value);
            }
        }
    }

    private void addSpecificHeadersForRecipient(Mail mail, MimeMessage message, MailAddress recipient) throws MessagingException {
        for (Header header: mail.getPerRecipientSpecificHeaders().getHeadersForRecipient(recipient)) {
            message.addHeader(header.getName(), header.getValue());
        }
    }
}
