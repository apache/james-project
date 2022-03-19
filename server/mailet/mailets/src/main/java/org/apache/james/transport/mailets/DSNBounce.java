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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.javax.MimeMultipartReport;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.MailModifier;
import org.apache.james.transport.mailets.redirect.NotifyMailetInitParameters;
import org.apache.james.transport.mailets.redirect.NotifyMailetsMessage;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.transport.mailets.redirect.TypeCode;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.james.transport.util.Patterns;
import org.apache.james.transport.util.RecipientsUtils;
import org.apache.james.transport.util.ReplyToUtils;
import org.apache.james.transport.util.SenderUtils;
import org.apache.james.transport.util.SpecialAddressesUtils;
import org.apache.james.transport.util.TosUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import scala.collection.mutable.StringBuilder;

/**
 * <p>
 * Generates a Delivery Status Notification (DSN) as per RFC-3464 An Extensible Message Format for Delivery Status
 * Notifications (https://tools.ietf.org/html/rfc3464).</p>
 *
 * <p>Note that this is different than a mail-client's reply, which would use the Reply-To or From header.</p>
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
 *   &lt;action&gt;<i>failed, delayed, delivered, expanded or relayed, default=failed</i>&lt;/action&gt;
 *   &lt;defaultStatus&gt;<i>SMTP status code. Try to adapt it to the mailet position: 2.0.0 for success, 4.0.0 for delays, 5.0.0 for failures default=unknown</i>&lt;/defaultStatus&gt;  &lt;!-- See https://tools.ietf.org/html/rfc3463 --&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 *
 * Possible values for defaultStatus (X being a digit):
 *  - General structure is X.XXX.XXX
 *  - 2.XXX.XXX indicates success and is suitable for relayed, delivered and expanded actions. 2.0.0 provides no further information.
 *  - 4.XXX.XXX indicates transient failures and is suitable for delayed action. 4.0.0 provides no further information.
 *  - 5.XXX.XXX indicates permanent failures and is suitable for failed. 5.0.0 provides no further information.
 *
 * @see RedirectNotify
 */

public class DSNBounce extends GenericMailet implements RedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSNBounce.class);

    enum Action {
        DELIVERED("Delivered", false),
        DELAYED("Delayed", true),
        FAILED("Failed", true),
        EXPANDED("Expanded", false),
        RELAYED("Relayed", false);

        public static Optional<Action> parse(String serialized) {
            return Stream.of(Action.values())
                .filter(value -> value.asString().equalsIgnoreCase(serialized))
                .findFirst();
        }

        private final String value;
        private final boolean shouldIncludeDiagnosticCode;

        Action(String value, boolean shouldIncludeDiagnosticCode) {
            this.value = value;
            this.shouldIncludeDiagnosticCode = shouldIncludeDiagnosticCode;
        }

        public String asString() {
            return value;
        }

        public boolean shouldIncludeDiagnostic() {
            return shouldIncludeDiagnosticCode;
        }
    }

    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of("debug", "passThrough", "messageString", "attachment", "sender", "prefix", "action", "defaultStatus");
    private static final List<MailAddress> RECIPIENT_MAIL_ADDRESSES = ImmutableList.of(SpecialAddress.REVERSE_PATH);
    private static final List<InternetAddress> TO_INTERNET_ADDRESSES = ImmutableList.of(SpecialAddress.REVERSE_PATH.toInternetAddress().get());

    private static final String LOCALHOST = "127.0.0.1";
    private static final Pattern DIAG_PATTERN = Patterns.compilePatternUncheckedException("^\\d{3}\\s.*$");
    private static final String MACHINE_PATTERN = "[machine]";
    private static final String LINE_BREAK = "\n";

    private final DNSService dns;
    private final DateTimeFormatter dateFormatter;
    private String messageString = null;
    private Action action = null;
    private String defaultStatus;

    @Inject
    public DSNBounce(DNSService dns) {
        this(dns, DateFormats.RFC822_DATE_FORMAT);
    }

    public DSNBounce(DNSService dns, DateTimeFormatter dateFormatter) {
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

        if (getInitParameters().isStatic() && getInitParameters().isDebug()) {
            LOGGER.debug(getInitParameters().asString());
        }
        messageString = getInitParameter("messageString",
                "Hi. This is the James mail server at [machine].\nI'm afraid I wasn't able to deliver your message to the following addresses.\nThis is a permanent error; I've given up. Sorry it didn't work out.  Below\nI include the list of recipients and the reason why I was unable to deliver\nyour message.\n");
        action = Optional.ofNullable(getInitParameter("action", null))
            .map(configuredValue -> Action.parse(configuredValue)
                .orElseThrow(() -> new IllegalArgumentException("Action '" + configuredValue + "' is not supported")))
            .orElse(Action.FAILED);
        defaultStatus = getInitParameter("defaultStatus", "unknown");
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
    public Set<String> getAllowedInitParameters() {
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
        return SpecialAddressesUtils.from(this).getFirstSpecialAddressIfMatchingOrGivenAddress(
                Optional.of(getInitParameters().getSender().orElse("postmaster")),
                RedirectNotify.SENDER_ALLOWED_SPECIALS);
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
        MailImpl.Builder newMailBuilder = MailImpl.duplicateWithoutMessage(originalMail);

        newMailBuilder.remoteHost(getRemoteHost());
        newMailBuilder.remoteAddr(getRemoteAddr());
        List<MailAddress> recipients = getSenderAsList(originalMail);
        newMailBuilder.addRecipients(recipients);
        newMailBuilder.sender(originalMail.getMaybeSender());

        if (getInitParameters().isDebug()) {
            LOGGER.debug("New mail - sender: {}, recipients: {}, name: {}, remoteHost: {}, remoteAddr: {}, state: {}, lastUpdated: {}, errorMessage: {}",
                originalMail.getMaybeSender(), recipients, newMailBuilder.getName(), getRemoteHost(), getRemoteAddr(), originalMail.getState(), originalMail.getLastUpdated(), originalMail.getErrorMessage());
        }

        MimeMessageWrapper bounceMessage = new MimeMessageWrapper(createBounceMessage(originalMail));
        try {
            MailImpl newMail = newMailBuilder.build();
            newMail.setMessageNoCopy(bounceMessage);

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
            LifecycleUtil.dispose(bounceMessage);
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
            return dateFormatter.format(ZonedDateTime.now());
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
            multipart.addBodyPart(createAttachedOriginal(originalMail, getAttachmentType(originalMail)));
        }
        return multipart;
    }

    private TypeCode getAttachmentType(Mail originalMail) {
        return originalMail.dsnParameters()
            .flatMap(DsnParameters::getRetParameter)
            .map(ret -> {
                switch (ret) {
                    case HDRS:
                        return TypeCode.HEADS;
                    case FULL:
                        return TypeCode.MESSAGE;
                    default:
                        throw new NotImplementedException("Unknown RET parameter: " + ret);
                }
            })
            .orElse(getInitParameters().getAttachmentType());
    }

    private MimeBodyPart createTextMsg(Mail originalMail) throws MessagingException {
        StringBuilder builder = new StringBuilder();

        builder.append(bounceMessage()).append(LINE_BREAK);
        Optional.ofNullable(originalMail.getMessage().getSubject())
            .ifPresent(subject -> builder.append("Original email subject: ").append(subject).append(LINE_BREAK).append(LINE_BREAK));
        builder.append(action.asString()).append(" recipient(s):").append(LINE_BREAK);
        builder.append(originalMail.getRecipients()
                .stream()
                .map(MailAddress::asString)
                .collect(Collectors.joining(", ")));
        builder.append(LINE_BREAK).append(LINE_BREAK);
        if (action.shouldIncludeDiagnostic()) {
            Optional<String> deliveryError = AttributeUtils.getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class);

            deliveryError.or(() -> Optional.ofNullable(originalMail.getErrorMessage()))
                .ifPresent(message -> {
                    builder.append("Error message:").append(LINE_BREAK);
                    builder.append(message).append(LINE_BREAK);
                    builder.append(LINE_BREAK);
                });
        }

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(builder.toString());
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
        StringBuilder buffer = new StringBuilder();

        appendReportingMTA(buffer);
        buffer.append("Received-From-MTA: dns; " + originalMail.getRemoteHost())
            .append(LINE_BREAK);

        originalMail.dsnParameters()
            .flatMap(DsnParameters::getEnvIdParameter)
            .ifPresent(envId -> buffer.append("Original-Envelope-Id: ")
                .append(envId.asString())
                .append(LINE_BREAK));
        originalMail.getAttribute(AttributeName.of("dsn-arrival-date"))
            .map(Attribute::getValue)
            .map(AttributeValue::value)
            .filter(ZonedDateTime.class::isInstance)
            .map(ZonedDateTime.class::cast)
            .ifPresent(arrivalDate -> buffer.append("Arrival-Date: ")
                .append(arrivalDate.format(dateFormatter))
                .append(LINE_BREAK));

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

    private void appendReportingMTA(StringBuilder buffer) {
        try {
            buffer.append("Reporting-MTA: dns; " + dns.getHostName(dns.getLocalHost()))
                .append(LINE_BREAK);
        } catch (Exception e) {
            LOGGER.error("Sending DSN without required Reporting-MTA Address", e);
        }
    }

    private void appendRecipient(StringBuilder buffer, MailAddress mailAddress, String deliveryError, Date lastUpdated) {
        buffer.append(LINE_BREAK);
        buffer.append("Final-Recipient: rfc822; " + mailAddress.toString()).append(LINE_BREAK);
        buffer.append("Action: ").append(action.asString().toLowerCase(Locale.US)).append(LINE_BREAK);
        buffer.append("Status: " + deliveryError).append(LINE_BREAK);
        if (action.shouldIncludeDiagnostic()) {
            buffer.append("Diagnostic-Code: " + getDiagnosticType(deliveryError) + "; " + deliveryError).append(LINE_BREAK);
        }
        buffer.append("Last-Attempt-Date: " + dateFormatter.format(ZonedDateTime.ofInstant(lastUpdated.toInstant(), ZoneId.systemDefault())))
            .append(LINE_BREAK);
    }

    private String getDeliveryError(Mail originalMail) {
        return AttributeUtils
            .getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class)
            .orElse(defaultStatus);
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
}
