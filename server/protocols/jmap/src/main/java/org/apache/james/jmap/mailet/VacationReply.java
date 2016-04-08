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

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class VacationReply {

    public static Builder builder(Mail originalMail) {
        return new Builder(originalMail);
    }

    public static class Builder {

        public static final boolean NOT_REPLY_TO_ALL = false;
        private final Mail originalMail;
        private MailAddress mailRecipient;
        private String reason;

        private Builder(Mail originalMail) {
            Preconditions.checkNotNull(originalMail, "Origin mail shall not be null");
            this.originalMail = originalMail;
        }

        public Builder receivedMailRecipient(MailAddress mailRecipient) {
            Preconditions.checkNotNull(mailRecipient);
            this.mailRecipient = mailRecipient;
            return this;
        }

        public Builder reason(String reason) {
            Preconditions.checkNotNull(reason);
            this.reason = reason;
            return this;
        }

        public VacationReply build() throws MessagingException {
            Preconditions.checkState(mailRecipient != null, "Original recipient address should not be null");
            Preconditions.checkState(originalMail.getSender() != null, "Original sender address should not be null");

            return new VacationReply(mailRecipient, ImmutableList.of(originalMail.getSender()), generateMimeMessage());
        }

        private MimeMessage generateMimeMessage() throws MessagingException {
            MimeMessage reply = (MimeMessage) originalMail.getMessage().reply(NOT_REPLY_TO_ALL);
            reply.setText(reason);
            reply.setHeader("from", mailRecipient.toString());
            reply.setHeader("to", originalMail.getSender().toString());
            return reply;
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

