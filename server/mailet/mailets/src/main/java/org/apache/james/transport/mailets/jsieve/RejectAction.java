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
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.AddressException;
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
import com.google.common.collect.ImmutableList;

/**
 * Performs the rejection of a mail, with a reply to the sender. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class RejectAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RejectAction.class);

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
        Optional<OriginalMessageId> originalMessageId = Optional.ofNullable(aMail.getMessage().getMessageID())
            .map(MimeUtility::unfold)
            .map(String::trim)
            .filter(messageId -> !messageId.isEmpty())
            .filter(messageId -> messageId.indexOf('\r') < 0 && messageId.indexOf('\n') < 0)
            .map(OriginalMessageId::new);

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

        // Send the message
        MimeMessage reply;
        try {
            reply = (MimeMessage) aMail.getMessage().reply(false);
        } catch (AddressException e) {
            // The message is refused anyway: nothing to answer to
            LOGGER.info("Unable to send reject MDN. Could not parse the recipient.", e);
            return;
        }
        context.getRecipient().toInternetAddress()
            .ifPresent(Throwing.<Address>consumer(reply::setFrom).sneakyThrow());
        reply.setContent(multipart);
        reply.saveChanges();
        ImmutableList<MailAddress> recipients = usableRecipients(reply);
        if (!recipients.isEmpty()) {
            MailImpl mail = MailImpl.builder()
                .name(MailImpl.getId())
                .addRecipients(recipients)
                .mimeMessage(reply)
                .build();
            try {
                context.post(mail);
            } finally {
                LifecycleUtil.dispose(mail);
            }
        } else {
            LOGGER.info("Unable to send reject MDN. Could not determine the recipient.");
        }
    }

    private ImmutableList<MailAddress> usableRecipients(MimeMessage reply) throws MessagingException {
        return Optional.ofNullable(reply.getAllRecipients())
            .stream()
            .flatMap(Arrays::stream)
            .map(InternetAddress.class::cast)
            .flatMap(this::asMailAddress)
            .collect(ImmutableList.toImmutableList());
    }

    // Jakarta Mail accepts values in From and Reply-To that MailAddress refuses
    private Stream<MailAddress> asMailAddress(InternetAddress address) {
        try {
            return Stream.of(new MailAddress(address));
        } catch (AddressException e) {
            LOGGER.info("Ignoring an unusable recipient for the reject MDN: {}", address, e);
            return Stream.empty();
        }
    }
}
