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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.james.transport.util.MailAddressUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MailModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailModifier.class);

    private static final String LOCAHOST = "127.0.0.1";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private RedirectNotify mailet;
        private MailImpl mail;
        private DNSService dns;

        private Builder() {
        }

        public Builder mailet(RedirectNotify mailet) {
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

    private final RedirectNotify mailet;
    private final MailImpl mail;
    private final DNSService dns;

    private MailModifier(RedirectNotify mailet, MailImpl mail, DNSService dns) {
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
            mail.setRecipients(recipients
                .stream()
                .collect(ImmutableList.toImmutableList()));
            if (mailet.getInitParameters().isDebug()
                    && LOGGER.isDebugEnabled()) {
                LOGGER.debug("recipients set to: {}", (Object) recipients.toArray());
            }
        }
    }

    public void setTo(List<MailAddress> mailAddresses) throws MessagingException {
        if (!mailAddresses.isEmpty()) {
            InternetAddress[] internetAddresses = MailAddressUtils.toInternetAddressArray(mailAddresses);
            mail.getMessage().setRecipients(Message.RecipientType.TO, internetAddresses);
            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("apparentlyTo set to: {}", (Object) internetAddresses);
            }
        }
    }

    public void setSubjectPrefix(Mail originalMail) throws MessagingException {
        new MimeMessageModifier(mail.getMessage())
            .replaceSubject(mailet.getSubjectPrefix(mail, mailet.getInitParameters().getSubjectPrefix(), originalMail));
    }

    /**
     * <p>
     * Sets the "Reply-To:" header of <i>newMail</i> to <i>replyTo</i>.
     * </p>
     * If the requested value is <code>SpecialAddress.NULL</code> will remove
     * the "Reply-To:" header. If the requested value is null does nothing.</p>
     */
    public void setReplyTo(Optional<MailAddress> optionalReplyTo) throws MessagingException {
        optionalReplyTo.ifPresent(Throwing
            .consumer((ThrowingConsumer<MailAddress>) this::setReplyTo)
            .sneakyThrow());
    }

    private void setReplyTo(MailAddress replyTo) throws MessagingException {
        if (replyTo.equals(SpecialAddress.NULL)) {
            mail.getMessage().setReplyTo(null);
            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("replyTo set to: null");
            }
        } else {
            ImmutableList<InternetAddress> replyToAsList = replyTo.toInternetAddress()
                .stream()
                .collect(ImmutableList.toImmutableList());
            mail.getMessage().setReplyTo(replyToAsList.toArray(new InternetAddress[0]));
            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("replyTo set to: {}", replyTo);
            }
        }
    }

    /**
     * Sets the "reverse-path" of <i>newMail</i> to <i>reversePath</i>. If the
     * requested value is <code>SpecialAddress.NULL</code> sets it to "<>". If
     * the requested value is null does nothing.
     */
    public void setReversePath(Optional<MailAddress> maybeReversePath) {
        if (maybeReversePath.isPresent()) {
            MailAddress reversePath = maybeReversePath.get();
            if (reversePath.equals(SpecialAddress.NULL)) {
                mail.setSender(null);
                if (mailet.getInitParameters().isDebug()) {
                    LOGGER.debug("reversePath set to: null");
                }
            } else {
                mail.setSender(reversePath);
                if (mailet.getInitParameters().isDebug()) {
                    LOGGER.debug("reversePath set to: {}", reversePath);
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
                    LOGGER.debug("IN_REPLY_TO set to: {}", messageId);
                }
            }
        }
    }

    public void setSender(Optional<MailAddress> sender) throws MessagingException {
        if (sender.isPresent()) {
            sender.flatMap(MailAddress::toInternetAddress)
                .ifPresent(Throwing.<Address>consumer(address -> mail.getMessage().setFrom(address)).sneakyThrow());

            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("sender set to: {}", sender);
            }
        }
    }

    public void initializeDateIfNotPresent() throws MessagingException {
        if (mail.getMessage().getHeader(RFC2822Headers.DATE) == null) {
            mail.getMessage().setHeader(RFC2822Headers.DATE, DateFormats.RFC822_DATE_FORMAT.format(ZonedDateTime.now()));
        }
    }

    public void setMessageId(String messageId) throws MessagingException {
        if (messageId != null) {
            mail.getMessage().setHeader(RFC2822Headers.MESSAGE_ID, messageId);
            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("MESSAGE_ID restored to: {}", messageId);
            }
        }
    }

    public MailImpl getMail() {
        return mail;
    }
}
