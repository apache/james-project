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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mdn.MDN;
import org.apache.james.mdn.MDNReport;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.server.core.MailImpl;
import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionReject;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * Performs the rejection of a mail, with a reply to the sender. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class RejectAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RejectAction.class);
    private static final String REPLY_PREFIX = "Re: ";

    @Override
    public void execute(Action action, Mail mail, ActionContext context)
            throws MessagingException {
        if (action instanceof ActionReject) {
            final ActionReject actionReject = (ActionReject) action;
            execute(actionReject, mail, context);
            DiscardAction.removeRecipient(mail, context);
        }
    }

    /**
     * <p>
     * Method execute executes the passed ActionReject. It sends an RFC 2098
     * compliant reject MDN back to the sender.
     * </p>
     * <p>
     * NOTE: The Mimecontent type should be 'report', but as we do not yet have
     * a DataHandler for this yet, its currently 'text'!
     * 
     * @param anAction not null
     * @param aMail not null
     * @param context not null
     * @throws MessagingException
     */
    public void execute(ActionReject anAction, Mail aMail, ActionContext context) throws MessagingException {
        ActionUtils.detectAndHandleLocalLooping(aMail, context, "reject");

        // RFC 5429 section 2.1: the MDN goes to the envelope sender, and never to a null one
        Optional<MailAddress> returnPath = aMail.getMaybeSender().asOptional();
        Optional<InternetAddress> returnPathAddress = returnPath.flatMap(MailAddress::toInternetAddress);
        if (returnPath.isEmpty() || returnPathAddress.isEmpty()) {
            LOGGER.info("Not sending a reject MDN to a null or unusable envelope sender");
            return;
        }

        // Create the MDN part
        StringBuilder humanText = new StringBuilder(128);
        humanText.append("This message was refused by the recipient's mail filtering program.");
        humanText.append("\r\n");
        humanText.append("The reason given was:");
        humanText.append("\r\n");
        humanText.append("\r\n");
        humanText.append(anAction.getMessage());

        String reportingUAName = null;
        try {
            reportingUAName = InetAddress.getLocalHost()
                    .getCanonicalHostName();
        } catch (UnknownHostException ex) {
            reportingUAName = "localhost";
        }

        String reportingUAProduct = context.getServerInfo();

        String[] originalRecipients = aMail.getMessage().getHeader(
                "Original-Recipient");
        String originalRecipient = null;
        if (null != originalRecipients && originalRecipients.length > 0) {
            originalRecipient = originalRecipients[0];
        }

        String finalRecipient = context.getRecipient().asString();
        // Original-Message-ID is optional (RFC 8098 3.2.5): omit it when the message has no usable Message-ID
        Optional<String> messageId = Optional.ofNullable(aMail.getMessage().getMessageID())
            .map(MimeUtility::unfold)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .filter(id -> id.indexOf('\r') < 0 && id.indexOf('\n') < 0);
        Optional<OriginalMessageId> originalMessageId = messageId.map(OriginalMessageId::new);

        MDNReport.Builder reportBuilder = MDNReport.builder();
        originalMessageId.ifPresent(reportBuilder::originalMessageIdField);

        Multipart multipart = MDN.builder()
            .humanReadableText(humanText.toString())
            .report(
                reportBuilder
                    .reportingUserAgentField(ReportingUserAgent.builder().userAgentName(reportingUAName).userAgentProduct(reportingUAProduct).build())
                    .finalRecipientField(finalRecipient)
                    .originalRecipientField(originalRecipient)
                    .dispositionField(Disposition.builder()
                        .actionMode(DispositionActionMode.Automatic)
                        .sendingMode(DispositionSendingMode.Automatic)
                        .type(DispositionType.Deleted)
                        .addModifier(DispositionModifier.Error)
                        .build())
                    .build())
            .build()
            .asMultipart();

        // Send the message. The From and Reply-To of the refused message are not used: anyone can write them.
        MimeMessage reply = new MimeMessage(Session.getInstance(new Properties()));
        replySubject(aMail.getMessage()).ifPresent(Throwing.<String>consumer(subject -> reply.setHeader("Subject", subject)).sneakyThrow());
        messageId.ifPresent(Throwing.<String>consumer(id -> reply.setHeader("In-Reply-To", id)).sneakyThrow());
        context.getRecipient().toInternetAddress()
            .ifPresent(Throwing.<Address>consumer(reply::setFrom).sneakyThrow());
        reply.setRecipient(Message.RecipientType.TO, returnPathAddress.get());
        // RFC 3834 section 5: lets responders recognize the MDN and not answer it
        reply.setHeader("Auto-Submitted", "auto-replied");
        reply.setContent(multipart);
        reply.saveChanges();

        MailImpl mail = MailImpl.builder()
            .name(MailImpl.getId())
            .addRecipient(returnPath.get())
            .mimeMessage(reply)
            .build();
        try {
            context.post(mail);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    // Same subject as MimeMessage::reply
    private Optional<String> replySubject(MimeMessage message) throws MessagingException {
        return Optional.ofNullable(message.getHeader("Subject", null))
            .map(subject -> subject.regionMatches(true, 0, REPLY_PREFIX, 0, REPLY_PREFIX.length()) ? subject : REPLY_PREFIX + subject);
    }
}
