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

import static org.apache.mailet.LoopPrevention.RECORDED_RECIPIENTS_ATTRIBUTE_NAME;

import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessRedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessRedirectNotify.class);

    public static ProcessRedirectNotify from(RedirectNotify mailet) {
        return new ProcessRedirectNotify(mailet);
    }

    private final RedirectNotify mailet;
    private final boolean isDebug;

    private ProcessRedirectNotify(RedirectNotify mailet) {
        this.mailet = mailet;
        this.isDebug = mailet.getInitParameters().isDebug();
    }

    public void process(Mail originalMail) throws MessagingException {

        // duplicates the Mail object, to be able to modify the new mail keeping
        // the original untouched
        String originalMessageId = originalMail.getMessage().getMessageID();
        MailImpl newMail = MailImpl.duplicate(originalMail);
        try {
            MailModifier mailModifier = MailModifier.builder()
                    .mailet(mailet)
                    .mail(newMail)
                    .dns(mailet.getDNSService())
                    .build();
            mailModifier.setRemoteAddr();
            mailModifier.setRemoteHost();

            if (mailet.getInitParameters().isDebug()) {
                LOGGER.debug("New mail - sender: {}, recipients: {}, name: {}, remoteHost: {}, remoteAddr: {}, state: {}, lastUpdated: {}, errorMessage: {}",
                        newMail.getMaybeSender(), newMail.getRecipients(), newMail.getName(), newMail.getRemoteHost(), newMail.getRemoteAddr(), newMail.getState(), newMail.getLastUpdated(), newMail.getErrorMessage());
            }

            // Create the message
            boolean keepMessageId = keepMessageId();
            if (!keepMessageId) {
                createAlterMessage(originalMail, newMail);
            } else {
                createUnalteredMessage(originalMail, newMail);
            }

            // Set additional headers

            mailModifier.setRecipients(mailet.getRecipients(originalMail));

            mailModifier.setTo(mailet.getTo(originalMail));
            mailModifier.setSubjectPrefix(originalMail);
            mailModifier.setReplyTo(mailet.getReplyTo(originalMail));
            mailModifier.setReversePath(mailet.getReversePath(originalMail));
            mailModifier.setIsReply(mailet.getInitParameters().isReply(), originalMail);
            mailModifier.setSender(mailet.getSender(originalMail));
            mailModifier.initializeDateIfNotPresent();
            if (keepMessageId) {
                mailModifier.setMessageId(originalMessageId);
            }
            finalizeMail(newMail);

            if (senderDomainIsValid(newMail)) {
                // Send it off...
                if (!newMail.getRecipients().isEmpty()) {
                    mailet.getMailetContext().sendMail(newMail);
                }
            } else {
                throw new MessagingException(mailet.getMailetName() + " mailet cannot forward " + originalMail.getName() + ". " +
                        "Invalid sender domain for " + newMail.getMaybeSender().asString() + ". " +
                        "Consider using the Resend mailet " + "using a different sender.");
            }

        } finally {
            newMail.dispose();
        }

        if (!mailet.getInitParameters().getPassThrough()) {
            originalMail.setState(Mail.GHOST);
        }
    }

    private void finalizeMail(MailImpl mail) throws MessagingException {
        mail.getMessage().saveChanges();
        Optional<Attribute> recordedRecipients = mail.getAttribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME);
        mail.removeAllAttributes();
        recordedRecipients.ifPresent(mail::setAttribute);
    }

    private boolean keepMessageId() {
        return mailet.getInitParameters().getInLineType().equals(TypeCode.UNALTERED);
    }

    private void createAlterMessage(Mail originalMail, MailImpl newMail) throws MessagingException {
        if (isDebug) {
            LOGGER.debug("Alter message");
        }
        MimeMessage oldMessage = newMail.getMessage();
        MimeMessage newMessage = MessageAlteringUtils.from(mailet)
            .originalMail(originalMail)
            .alteredMessage();
        newMail.setMessage(newMessage);
        LifecycleUtil.dispose(oldMessage);
    }

    private void createUnalteredMessage(Mail originalMail, MailImpl newMail) throws MessagingException {
        // if we need the original, create a copy of this message to
        // redirect
        if (mailet.getInitParameters().getPassThrough()) {
            newMail.setMessage(new CopiedMimeMessage(originalMail.getMessage()));
        }
        if (isDebug) {
            LOGGER.debug("Message resent unaltered.");
        }
    }

    private static class CopiedMimeMessage extends MimeMessage {

        public CopiedMimeMessage(MimeMessage originalMessage) throws MessagingException {
            super(originalMessage);
        }

        @Override
        protected void updateHeaders() throws MessagingException {
            if (getMessageID() == null) {
                super.updateHeaders();
            } else {
                modified = false;
            }
        }
    }

    /**
     * <p>
     * Checks if a sender domain of <i>mail</i> is valid.
     * </p>
     * <p>
     * If we do not do this check, and someone uses a redirection mailet in a
     * processor initiated by SenderInFakeDomain, then a fake sender domain will
     * cause an infinite loop (the forwarded e-mail still appears to come from a
     * fake domain).<br>
     * Although this can be viewed as a configuration error, the consequences of
     * such a mis-configuration are severe enough to warrant protecting against
     * the infinite loop.
     * </p>
     * <p>
     * This check can be skipped if {@link #getFakeDomainCheck(Mail)} returns
     * true.
     * </p>
     *
     * @param mail the mail object to check
     * @return true if the if the sender is null or
     *         {@link org.apache.mailet.MailetContext#getMailServers} returns
     *         true for the sender host part
     */
    @SuppressWarnings("deprecation")
    private boolean senderDomainIsValid(Mail mail) {
        return !mailet.getInitParameters().getFakeDomainCheck()
                || !mail.hasSender()
                || !mailet.getMailetContext()
            .getMailServers(mail.getMaybeSender().get()
                .getDomain())
            .isEmpty();
    }
}
