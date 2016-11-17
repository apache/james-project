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

import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.james.core.MailImpl;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.util.MailAddressUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class MailModifier {

    private static final String LOCAHOST = "127.0.0.1";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AbstractRedirect mailet;
        private MailImpl mail;
        private DNSService dns;

        private Builder() {
        }

        public Builder mailet(AbstractRedirect mailet) {
            this.mailet = mailet;
            return this;
        }

        public Builder mail(MailImpl mail) {
            this.mail = mail;
            return this;
        }

        public Builder dns(DNSService dns) {
            this.dns = dns;
            return this;
        }

        public MailModifier build() {
            Preconditions.checkNotNull(mailet, "'mailet' is mandatory");
            Preconditions.checkNotNull(mail, "'mail' is mandatory");
            Preconditions.checkNotNull(dns, "'dns' is mandatory");
            return new MailModifier(mailet, mail, dns);
        }
    }

    private final AbstractRedirect mailet;
    private final MailImpl mail;
    private final DNSService dns;

    private MailModifier(AbstractRedirect mailet, MailImpl mail, DNSService dns) {
        this.mailet = mailet;
        this.mail = mail;
        this.dns = dns;
    }

    public void setRemoteAddr() {
        try {
            mail.setRemoteAddr(dns.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            mail.setRemoteAddr(LOCAHOST);
        }
    }

    public void setRemoteHost() {
        try {
            mail.setRemoteHost(dns.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            mail.setRemoteHost("localhost");
        }
    }

    public void setRecipients(List<MailAddress> recipients) {
        if (!recipients.isEmpty()) {
            mail.setRecipients(recipients);
            if (mailet.getInitParameters().isDebug()) {
                mailet.log("recipients set to: " + mailet.arrayToString(recipients.toArray()));
            }
        }
    }

    public void setTo(List<MailAddress> mailAddresses) throws MessagingException {
        if (!mailAddresses.isEmpty()) {
            InternetAddress[] internetAddresses = MailAddressUtils.toInternetAddressArray(mailAddresses);
            mail.getMessage().setRecipients(Message.RecipientType.TO, internetAddresses);
            if (mailet.getInitParameters().isDebug()) {
                mailet.log("apparentlyTo set to: " + internetAddresses);
            }
        }
    }

    public void setSubjectPrefix(Mail originalMail) throws MessagingException {
        mailet.getMimeMessageModifier(mail, originalMail)
            .replaceSubject(mailet.getSubjectPrefix(mail, mailet.getInitParameters().getSubjectPrefix(), originalMail));
    }

    /**
     * <p>
     * Sets the "Reply-To:" header of <i>newMail</i> to <i>replyTo</i>.
     * </p>
     * If the requested value is <code>SpecialAddress.NULL</code> will remove
     * the "Reply-To:" header. If the requested value is null does nothing.</p>
     */
    public void setReplyTo(MailAddress replyTo, Mail originalMail) throws MessagingException {
        if (replyTo != null) {
            if (replyTo.equals(SpecialAddress.NULL)) {
                mail.getMessage().setReplyTo(null);
                if (mailet.getInitParameters().isDebug()) {
                    mailet.log("replyTo set to: null");
                }
            } else {
                mail.getMessage().setReplyTo(new InternetAddress[] { replyTo.toInternetAddress() });
                if (mailet.getInitParameters().isDebug()) {
                    mailet.log("replyTo set to: " + replyTo);
                }
            }
        }
    }

    /**
     * Sets the "reverse-path" of <i>newMail</i> to <i>reversePath</i>. If the
     * requested value is <code>SpecialAddress.NULL</code> sets it to "<>". If
     * the requested value is null does nothing.
     */
    public void setReversePath(MailAddress reversePath, Mail originalMail) {
        if (reversePath != null) {
            if (reversePath.equals(SpecialAddress.NULL)) {
                mail.setSender(null);
                if (mailet.getInitParameters().isDebug()) {
                    mailet.log("reversePath set to: null");
                }
            } else {
                mail.setSender(reversePath);
                if (mailet.getInitParameters().isDebug()) {
                    mailet.log("reversePath set to: " + reversePath);
                }
            }
        }
    }

    /**
     * Sets the "In-Reply-To:" header of <i>newMail</i> to the "Message-Id:" of
     * <i>originalMail</i>, if <i>isReply</i> is true.
     */
    public void setIsReply(boolean isReply, Mail originalMail) throws MessagingException {
        if (isReply) {
            String messageId = originalMail.getMessage().getMessageID();
            if (messageId != null) {
                mail.getMessage().setHeader(RFC2822Headers.IN_REPLY_TO, messageId);
                if (mailet.getInitParameters().isDebug()) {
                    mailet.log("IN_REPLY_TO set to: " + messageId);
                }
            }
        }
    }

    public void setSender(Optional<MailAddress> sender, Mail originalMail) throws MessagingException {
        if (sender.isPresent()) {
            mail.getMessage().setFrom(sender.get().toInternetAddress());

            if (mailet.getInitParameters().isDebug()) {
                mailet.log("sender set to: " + sender);
            }
        }
    }

    public void initializeDateIfNotPresent() throws MessagingException {
        if (mail.getMessage().getHeader(RFC2822Headers.DATE) == null) {
            mail.getMessage().setHeader(RFC2822Headers.DATE, DateFormats.RFC822_DATE_FORMAT.format(new Date()));
        }
    }

    public void setMessageId(Mail originalMail) throws MessagingException {
        String messageId = originalMail.getMessage().getMessageID();
        if (messageId != null) {
            mail.getMessage().setHeader(RFC2822Headers.MESSAGE_ID, messageId);
            if (mailet.getInitParameters().isDebug()) {
                mailet.log("MESSAGE_ID restored to: " + messageId);
            }
        }
    }

    public MailImpl getMail() {
        return mail;
    }
}
