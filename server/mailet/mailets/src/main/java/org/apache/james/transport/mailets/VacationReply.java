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

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.util.MimeMessageBodyGenerator;
import org.apache.james.vacation.api.Vacation;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

public class VacationReply {

    public static final String FROM_HEADER = "from";
    public static final String TO_HEADER = "to";

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

        public VacationReply build(MimeMessageBodyGenerator mimeMessageBodyGenerator) throws MessagingException {
            Preconditions.checkState(mailRecipient != null, "Original recipient address should not be null");
            Preconditions.checkState(originalMail.hasSender(), "Original sender address should not be null");

            return new VacationReply(mailRecipient, originalMail.getMaybeSender().asList(), generateMimeMessage(mimeMessageBodyGenerator));
        }

        private MimeMessage generateMimeMessage(MimeMessageBodyGenerator mimeMessageBodyGenerator) throws MessagingException {
            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(NOT_REPLY_TO_ALL);
            vacation.getSubject().ifPresent(Throwing.consumer(subjectString -> reply.setSubject(subjectString)));
            reply.setHeader(FROM_HEADER, mailRecipient.toString());
            reply.setHeader(TO_HEADER, originalMail.getMaybeSender().get().asString());
            reply.setHeader(AutomaticallySentMailDetector.AUTO_SUBMITTED_HEADER, AutomaticallySentMailDetector.AUTO_REPLIED_VALUE);

            return mimeMessageBodyGenerator.from(reply, vacation.getTextBody(), vacation.getHtmlBody());
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

