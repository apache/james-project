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

package org.apache.james.jmap.mailet;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.AutomaticallySentMailDetector;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class VacationReply {

    public static final String FROM_HEADER = "from";
    public static final String TO_HEADER = "to";
    public static final String MIXED = "mixed";

    public static Builder builder(Mail originalMail) {
        return new Builder(originalMail);
    }

    public static class Builder {

        public static final boolean NOT_REPLY_TO_ALL = false;
        private final Mail originalMail;
        private MailAddress mailRecipient;
        private Vacation vacation;

        private Builder(Mail originalMail) {
            Preconditions.checkNotNull(originalMail, "Origin mail shall not be null");
            this.originalMail = originalMail;
        }

        public Builder receivedMailRecipient(MailAddress mailRecipient) {
            Preconditions.checkNotNull(mailRecipient);
            this.mailRecipient = mailRecipient;
            return this;
        }

        public Builder vacation(Vacation vacation) {
            this.vacation = vacation;
            return this;
        }

        public VacationReply build() throws MessagingException {
            Preconditions.checkState(mailRecipient != null, "Original recipient address should not be null");
            Preconditions.checkState(originalMail.getSender() != null, "Original sender address should not be null");

            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(false);
            reply.setContent(generateMultipart());

            return new VacationReply(mailRecipient, ImmutableList.of(originalMail.getSender()), generateMimeMessage());
        }

        private MimeMessage generateMimeMessage() throws MessagingException {
            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(NOT_REPLY_TO_ALL);
            vacation.getSubject().ifPresent(Throwing.consumer(subjectString -> reply.setHeader("subject", subjectString)));
            reply.setHeader(FROM_HEADER, mailRecipient.toString());
            reply.setHeader(TO_HEADER, originalMail.getSender().toString());
            reply.setHeader(AutomaticallySentMailDetector.AUTO_SUBMITTED_HEADER, AutomaticallySentMailDetector.AUTO_REPLIED_VALUE);

            return addBody(reply);
        }

        private MimeMessage addBody(MimeMessage reply) throws MessagingException {
            if (! vacation.getHtmlBody().isPresent()) {
                reply.setText(vacation.getTextBody().get());
            } else {
                reply.setContent(generateMultipart());
            }
            return reply;
        }


        private Multipart generateMultipart() throws MessagingException {
            try {
                Multipart multipart = new MimeMultipart(MIXED);
                addPlainPart(multipart, vacation.getTextBody());
                addHtmlPart(multipart, vacation.getHtmlBody());
                return multipart;
            } catch (IOException e) {
                throw new MessagingException("Cannot read specified content", e);
            }
        }

        private Multipart addPlainPart(Multipart multipart, Optional<String> textBody) throws MessagingException, IOException {
            textBody.ifPresent(Throwing.consumer(text -> addTextPart(multipart, text, ContentTypeField.TYPE_TEXT_PLAIN)));
            return multipart;
        }

        private Multipart addHtmlPart(Multipart multipart, Optional<String> htmlBody) throws MessagingException, IOException {
            htmlBody.ifPresent(Throwing.consumer(html -> addTextPart(multipart, html, "text/html")));
            return multipart;
        }

        private Multipart addTextPart(Multipart multipart, String text, String contentType) throws MessagingException, IOException {
            MimeBodyPart textReasonPart = new MimeBodyPart();
            textReasonPart.setDataHandler(
                new DataHandler(
                    new ByteArrayDataSource(
                        text,
                        contentType + "; charset=UTF-8")));
            multipart.addBodyPart(textReasonPart);
            return multipart;
        }
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

