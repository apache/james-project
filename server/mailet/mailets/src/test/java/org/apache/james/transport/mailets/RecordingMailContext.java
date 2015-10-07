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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMailContext;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @since 15.12.12 12:02
 */
public class RecordingMailContext extends FakeMailContext {

    private final List<SendMailEvent> sendmails = new LinkedList<SendMailEvent>();

    @Override
    public boolean isLocalServer(String serverName) {
        return "localhost".equals(serverName);
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        sendmails.add(new SendMailEvent(mail));
    }

    @Override
    public void sendMail(MimeMessage msg) throws MessagingException {
       sendmails.add(new SendMailEvent(msg));
    }

    @Override
    public void sendMail(MailAddress sender, Collection recipients, MimeMessage msg) throws MessagingException {
        sendmails.add(new SendMailEvent(msg, sender, recipients));
    }

    @Override
    public void sendMail(MailAddress sender, Collection recipients, MimeMessage msg, String state) throws MessagingException {
        sendmails.add(new SendMailEvent(msg, sender, recipients, state));
    }

    public List<SendMailEvent> getSendmails() {
        return sendmails;
    }

    public static class SendMailEvent {
        private final Object message;
        private final MailAddress sender;
        private final Collection<MailAddress> recipients;
        private final String state;

        private SendMailEvent(Object message, MailAddress sender, Collection<MailAddress> recipients, String state) {
            this.message = message;
            this.sender = sender;
            this.recipients = recipients;
            this.state = state;
        }

        public SendMailEvent(Mail mail) {
            this(mail, null, null, null);
        }

        public SendMailEvent(MimeMessage mail) {
            this(mail, null, null, null);
        }

        public SendMailEvent(MimeMessage mail, MailAddress sender, Collection<MailAddress> recipients) {
            this(mail, sender, recipients, null);
        }
        public SendMailEvent(MimeMessage mail, MailAddress sender, Collection<MailAddress> recipients, String state) {
            this((Object)mail, sender, recipients, state);
        }

        public Mail getMail() {
            if (message instanceof Mail) {
                return (Mail) message;
            }
            return null;
        }
        public MimeMessage getMessage() {
            if (message instanceof MimeMessage) {
                return  (MimeMessage) message;
            }
            return null;
        }

        public MailAddress getSender() {
            return sender;
        }

        public Collection<MailAddress> getRecipients() {
            return recipients;
        }

        public String getState() {
            return state;
        }
    }

}
