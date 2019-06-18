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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.javax.MimeMultipartReport;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.managesieve.ManageSieveMailet;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.MailModifier;
import org.apache.james.transport.mailets.redirect.NotifyMailetInitParameters;
import org.apache.james.transport.mailets.redirect.NotifyMailetsMessage;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.transport.mailets.redirect.TypeCode;
import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.james.transport.util.Patterns;
import org.apache.james.transport.util.RecipientsUtils;
import org.apache.james.transport.util.ReplyToUtils;
import org.apache.james.transport.util.SenderUtils;
import org.apache.james.transport.util.SpecialAddressesUtils;
import org.apache.james.transport.util.TosUtils;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * Generates a Delivery Status Notification (DSN) Note that this is different
 * than a mail-client's reply, which would use the Reply-To or From header.
 * </p>
 * <p>
 * Bounced messages are attached in their entirety (headers and content) and the
 * resulting MIME part type is "message/rfc822".<br>
 * The reverse-path and the Return-Path header of the response is set to "null"
 * ("<>"), meaning that no reply should be sent.
 * </p>
 * <p>
 * A sender of the notification message can optionally be specified. If one is
 * not specified, the postmaster's address will be used.<br>
 * <p>
 * Supports the <code>passThrough</code> init parameter (true if missing).
 * </p>
 * <p/>
 * <p>
 * Sample configuration:
 * </p>
 * <p/>
 * <pre>
 * <code>
 * &lt;mailet match="All" class="DSNBounce">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered,
 *  default=postmaster</i>&lt;/sender&gt;
 *   &lt;prefix&gt;<i>optional subject prefix prepended to the original
 *  message</i>&lt;/prefix&gt;
 *   &lt;attachment&gt;<i>message, heads or none, default=message</i>&lt;/attachment&gt;
 *   &lt;messageString&gt;<i>the message sent in the bounce, the first occurrence of the pattern [machine] is replaced with the name of the executing machine, default=Hi. This is the James mail server at [machine] ... </i>&lt;/messageString&gt;
 *   &lt;passThrough&gt;<i>true or false, default=true</i>&lt;/passThrough&gt;
 *   &lt;debug&gt;<i>true or false, default=false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 *
 * @see org.apache.james.transport.mailets.AbstractNotify
 */

public class DSNBounce extends GenericMailet implements RedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveMailet.class);

    private static final String[] CONFIGURABLE_PARAMETERS = new String[]{ "debug", "passThrough", "messageString", "attachment", "sender", "prefix" };
    private static final List<MailAddress> RECIPIENT_MAIL_ADDRESSES = ImmutableList.of(SpecialAddress.REVERSE_PATH);
    private static final List<InternetAddress> TO_INTERNET_ADDRESSES = ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress());

    private static final String LOCALHOST = "127.0.0.1";
    private static final Pattern DIAG_PATTERN = Patterns.compilePatternUncheckedException("^\\d{3}\\s.*$");
    private static final String MACHINE_PATTERN = "[machine]";
    private static final String LINE_BREAK = "\n";
    private static final AttributeName DELIVERY_ERROR = AttributeName.of("delivery-error");

    private final DNSService dns;
    private final FastDateFormat dateFormatter;
    private String messageString = null;

    @Inject
    public DSNBounce(DNSService dns) {
        this(dns, DateFormats.RFC822_DATE_FORMAT);
    }

    public DSNBounce(DNSService dns, FastDateFormat dateFormatter) {
        this.dns = dns;
        this.dateFormatter = dateFormatter;
    }

    @Override
    public void init() throws MessagingException {
        if (getInitParameters().isDebug()) {
            LOGGER.debug("Initializing");
        }

        // check that all init parameters have been declared in
        // allowedInitParameters
        checkInitParameters(getAllowedInitParameters());

        if (getInitParameters().isStatic()) {
            if (getInitParameters().isDebug()) {
                LOGGER.debug(getInitParameters().asString());
            }
        }
        messageString = getInitParameter("messageString",
                "Hi. This is the James mail server at [machine].\nI'm afraid I wasn't able to deliver your message to the following addresses.\nThis is a permanent error; I've given up. Sorry it didn't work out.  Below\nI include the list of recipients and the reason why I was unable to deliver\nyour message.\n");
    }

    @Override
    public String getMailetInfo() {
        return "DSNBounce Mailet";
    }

    @Override
    public InitParameters getInitParameters() {
        return NotifyMailetInitParameters.from(this);
    }

    @Override
    public String[] getAllowedInitParameters() {
        return CONFIGURABLE_PARAMETERS;
    }

    @Override
    public DNSService getDNSService() {
        return dns;
    }

    @Override
    public String getMessage(Mail originalMail) throws MessagingException {
        return new NotifyMailetsMessage().generateMessage(getInitParameters().getMessage(), originalMail);
    }

    @Override
    public List<MailAddress> getRecipients() {
        return RECIPIENT_MAIL_ADDRESSES;
    }

    @Override
    public List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        return RecipientsUtils.from(this).getRecipients(originalMail);
    }

    @Override
    public List<InternetAddress> getTo() {
        return TO_INTERNET_ADDRESSES;
    }

    @Override
    public List<MailAddress> getTo(Mail originalMail) throws MessagingException {
        return TosUtils.from(this).getTo(originalMail);
    }

    @Override
    public Optional<MailAddress> getReplyTo() throws MessagingException {
        return Optional.of(SpecialAddress.NULL);
    }

    @Override
    public Optional<MailAddress> getReplyTo(Mail originalMail) throws MessagingException {
        return ReplyToUtils.from(getReplyTo()).getReplyTo(originalMail);
    }

    @Override
    public Optional<MailAddress> getReversePath() throws MessagingException {
        return SpecialAddressesUtils.from(this)
                .getFirstSpecialAddressIfMatchingOrGivenAddress(getInitParameters().getReversePath(), RedirectNotify.REVERSE_PATH_ALLOWED_SPECIALS);
    }

    @Override
    public Optional<MailAddress> getReversePath(Mail originalMail) {
        return Optional.of(SpecialAddress.NULL);
    }

    @Override
    public Optional<MailAddress> getSender() throws MessagingException {
        return SpecialAddressesUtils.from(this)
                .getFirstSpecialAddressIfMatchingOrGivenAddress(getInitParameters().getSender(), RedirectNotify.SENDER_ALLOWED_SPECIALS);
    }

    @Override
    public Optional<MailAddress> getSender(Mail originalMail) throws MessagingException {
        return SenderUtils.from(getSender()).getSender(originalMail);
    }

    @Override
    public Optional<String> getSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        return new MimeMessageUtils(originalMail.getMessage()).subjectWithPrefix(subjectPrefix);
    }

    @Override
    public void service(Mail originalMail) throws MessagingException {
        if (hasSender(originalMail)) {
            trySendBounce(originalMail);
        }

        if (!getInitParameters().getPassThrough()) {
            originalMail.setState(Mail.GHOST);
        }
    }

    private void trySendBounce(Mail originalMail) throws MessagingException {
        MailImpl newMail = MailImpl.duplicate(originalMail);
        try {
            newMail.setRemoteHost(getRemoteHost());
            newMail.setRemoteAddr(getRemoteAddr());
            newMail.setRecipients(getSenderAsList(originalMail));
       
            if (getInitParameters().isDebug()) {
                LOGGER.debug("New mail - sender: {}, recipients: {}, name: {}, remoteHost: {}, remoteAddr: {}, state: {}, lastUpdated: {}, errorMessage: {}",
                        newMail.getMaybeSender(), newMail.getRecipients(), newMail.getName(), newMail.getRemoteHost(), newMail.getRemoteAddr(), newMail.getState(), newMail.getLastUpdated(), newMail.getErrorMessage());
            }
       
            newMail.setMessage(createBounceMessage(originalMail));
       
            // Set additional headers
            MailModifier mailModifier = MailModifier.builder()
                    .mailet(this)
                    .mail(newMail)
                    .dns(dns)
                    .build();
            mailModifier.setRecipients(getRecipients(originalMail));
            mailModifier.setTo(getTo(originalMail));
            mailModifier.setSubjectPrefix(originalMail);
            mailModifier.setReplyTo(getReplyTo(originalMail));
            mailModifier.setReversePath(getReversePath(originalMail));
            mailModifier.setIsReply(getInitParameters().isReply(), originalMail);
            mailModifier.setSender(getSender(originalMail));
       
            newMail.getMessage().setHeader(RFC2822Headers.DATE, getDateHeader(originalMail));
       
            newMail.getMessage().saveChanges();
            getMailetContext().sendMail(newMail);
        } finally {
            newMail.dispose();
        }
    }

    private boolean hasSender(Mail originalMail) {
        if (!originalMail.hasSender()) {
            if (getInitParameters().isDebug()) {
                LOGGER.info("Processing a bounce request for a message with an empty reverse-path.  No bounce will be sent.");
            }
            return false;
        }
        return true;
    }

    private String getDateHeader(Mail originalMail) throws MessagingException {
        String[] date = originalMail.getMessage().getHeader(RFC2822Headers.DATE);
        if (date == null) {
            return dateFormatter.format(new Date());
        }
        return date[0];
    }

    private String getRemoteHost() {
        try {
            return dns.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private String getRemoteAddr() {
        try {
            return dns.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return LOCALHOST;
        }
    }

    private List<MailAddress> getSenderAsList(Mail originalMail) {
        MaybeSender reversePath = originalMail.getMaybeSender();

        if (getInitParameters().isDebug()) {
            LOGGER.debug("Processing a bounce request for a message with a reverse path.  The bounce will be sent to {}", reversePath.asString());
        }
        return reversePath.asList();
    }

    private MimeMessage createBounceMessage(Mail originalMail) throws MessagingException {
        MimeMultipartReport multipart = createMultipart(originalMail);

        MimeMessage newMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties(), null));
        newMessage.setContent(multipart);
        newMessage.setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());
        return newMessage;
    }

    private MimeMultipartReport createMultipart(Mail originalMail) throws MessagingException {
        MimeMultipartReport multipart = new MimeMultipartReport();
        multipart.setReportType("delivery-status");

        multipart.addBodyPart(createTextMsg(originalMail));
        multipart.addBodyPart(createDSN(originalMail));
        if (!getInitParameters().getAttachmentType().equals(TypeCode.NONE)) {
            multipart.addBodyPart(createAttachedOriginal(originalMail, getInitParameters().getAttachmentType()));
        }
        return multipart;
    }

    private MimeBodyPart createTextMsg(Mail originalMail) throws MessagingException {
        StringBuffer buffer = new StringBuffer();

        buffer.append(bounceMessage()).append(LINE_BREAK);
        buffer.append("Failed recipient(s):").append(LINE_BREAK);
        for (MailAddress mailAddress : originalMail.getRecipients()) {
            buffer.append(mailAddress);
        }
        buffer.append(LINE_BREAK).append(LINE_BREAK);
        buffer.append("Error message:").append(LINE_BREAK);
        buffer.append(AttributeUtils.getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class).orElse("")).append(LINE_BREAK);
        buffer.append(LINE_BREAK);

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(buffer.toString());
        return bodyPart;
    }


    private String getHostname() {
        try {
            InetAddress hostAddress = InetAddress.getLocalHost();
            return hostAddress.getHostName();
        } catch (Exception e) {
            return "[address unknown]";
        }
    }

    private String bounceMessage() {
        if (messageString.contains(MACHINE_PATTERN)) {
            return messageString.replace(MACHINE_PATTERN, getHostname());
        }
        return messageString;
    }

    private MimeBodyPart createDSN(Mail originalMail) throws MessagingException {
        StringBuffer buffer = new StringBuffer();

        appendReportingMTA(buffer);
        buffer.append("Received-From-MTA: dns; " + originalMail.getRemoteHost())
            .append(LINE_BREAK);

        for (MailAddress rec : originalMail.getRecipients()) {
            appendRecipient(buffer, rec, getDeliveryError(originalMail), originalMail.getLastUpdated());
        }

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(buffer.toString(), "text/plain");
        bodyPart.setHeader("Content-Type", "message/delivery-status");
        bodyPart.setDescription("Delivery Status Notification");
        bodyPart.setFileName("status.dat");
        return bodyPart;
    }

    private void appendReportingMTA(StringBuffer buffer) {
        try {
            buffer.append("Reporting-MTA: dns; " + dns.getHostName(dns.getLocalHost()))
                .append(LINE_BREAK);
        } catch (Exception e) {
            LOGGER.error("Sending DSN without required Reporting-MTA Address", e);
        }
    }

    private void appendRecipient(StringBuffer buffer, MailAddress mailAddress, String deliveryError, Date lastUpdated) {
        buffer.append(LINE_BREAK);
        buffer.append("Final-Recipient: rfc822; " + mailAddress.toString()).append(LINE_BREAK);
        buffer.append("Action: failed").append(LINE_BREAK);
        buffer.append("Status: " + deliveryError).append(LINE_BREAK);
        buffer.append("Diagnostic-Code: " + getDiagnosticType(deliveryError) + "; " + deliveryError).append(LINE_BREAK);
        buffer.append("Last-Attempt-Date: " + dateFormatter.format(lastUpdated))
            .append(LINE_BREAK);
    }

    private String getDeliveryError(Mail originalMail) {
        return AttributeUtils
            .getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class)
            .orElse("unknown");
    }

    private String getDiagnosticType(String diagnosticCode) {
        if (DIAG_PATTERN.matcher(diagnosticCode).matches()) {
            return "smtp";
        }
        return "X-James";
    }

    private MimeBodyPart createAttachedOriginal(Mail originalMail, TypeCode attachmentType) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        MimeMessage originalMessage = originalMail.getMessage();

        if (attachmentType.equals(TypeCode.HEADS)) {
            part.setContent(new MimeMessageUtils(originalMessage).getMessageHeaders(), "text/plain");
            part.setHeader("Content-Type", "text/rfc822-headers");
        } else {
            part.setContent(originalMessage, "message/rfc822");
        }

        if ((originalMessage.getSubject() != null) && (originalMessage.getSubject().trim().length() > 0)) {
            part.setFileName(originalMessage.getSubject().trim());
        } else {
            part.setFileName("No Subject");
        }
        part.setDisposition("Attachment");
        return part;
    }

    @Override
    public MimeMessageModifier getMimeMessageModifier(Mail newMail, Mail originalMail) throws MessagingException {
        return new MimeMessageModifier(originalMail.getMessage());
    }
}
