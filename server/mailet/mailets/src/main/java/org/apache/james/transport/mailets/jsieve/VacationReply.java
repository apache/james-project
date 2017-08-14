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

package org.apache.james.transport.mailets.jsieve;

import java.io.IOException;
import java.util.List;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class VacationReply {
    private static final Logger LOGGER = LoggerFactory.getLogger(VacationReply.class);

    public static class Builder {

        private final Mail originalMail;
        private final ActionContext context;
        private String from;
        private String reason;
        private String mime;
        private String subject;

        public Builder(Mail originalMail, ActionContext context) {
            this.originalMail = originalMail;
            this.context = context;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder mime(String mime) {
            this.mime = mime;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public VacationReply build() throws MessagingException {
            Preconditions.checkState(eitherReasonOrMime());
            ActionUtils.detectAndHandleLocalLooping(originalMail, context, "vacation");

            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(false);
            reply.setSubject(generateNotificationSubject());
            reply.setContent(generateNotificationContent());

            return new VacationReply(retrieveOriginalSender(), Lists.newArrayList(originalMail.getSender()), reply);
        }

        private boolean eitherReasonOrMime() {
            return (reason == null) ^ (mime == null);
        }

        private String generateNotificationSubject() {
            return Optional.fromNullable(subject)
                .or(context.getRecipient() + " is currently in vacation");
        }

        private Multipart generateNotificationContent() throws MessagingException {
            try {
                if (reason != null) {
                    return generateNotificationContentFromReasonString();
                } else {
                    return generateNotificationContentFromMime();
                }
            } catch (IOException e) {
                throw new MessagingException("Cannot read specified content", e);
            }
        }

        private Multipart generateNotificationContentFromMime() throws MessagingException, IOException {
            return new MimeMultipart(new ByteArrayDataSource(mime, "mixed"));
        }

        private Multipart generateNotificationContentFromReasonString() throws MessagingException, IOException {
            Multipart multipart = new MimeMultipart("mixed");
            MimeBodyPart reasonPart = new MimeBodyPart();
            reasonPart.setDataHandler(
                new DataHandler(
                    new ByteArrayDataSource(
                        reason,
                        "text/plain; charset=UTF-8")));
            reasonPart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(reasonPart);
            return multipart;
        }

        private MailAddress retrieveOriginalSender() throws AddressException {
            return Optional.fromNullable(from)
                .transform(address -> retrieveAddressFromString(address, context))
                .or(context.getRecipient());
        }

        private MailAddress retrieveAddressFromString(String address, ActionContext context) {
            try {
                return new MailAddress(address);
            } catch (AddressException e) {
                LOGGER.warn("Mail address " + address + " was not well formatted : " + e.getLocalizedMessage());
                return null;
            }
        }
    }

    public static Builder builder(Mail originalMail, ActionContext context) {
        return new Builder(originalMail, context);
    }

    private final MailAddress sender;
    private final List<MailAddress> recipients;
    private final MimeMessage mimeMessage;

    private VacationReply(MailAddress sender, List<MailAddress> recipients, MimeMessage mimeMessage) {
        this.sender = sender;
        this.recipients = recipients;
        this.mimeMessage = mimeMessage;
    }

    public MailAddress getSender() {
        return sender;
    }

    public List<MailAddress> getRecipients() {
        return recipients;
    }

    public MimeMessage getMimeMessage() {
        return mimeMessage;
    }
}
