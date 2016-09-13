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

import java.io.ByteArrayOutputStream;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.ParseException;

import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageUtil;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.Redirect;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Abstract mailet providing configurable redirection services.<br>
 * This mailet can be subclassed to make authoring redirection mailets simple.<br>
 * By extending it and overriding one or more of these methods new behaviour can
 * be quickly created without the author having to address any other issue than
 * the relevant one:
 * </p>
 * <ul>
 * <li>attachError() , should error messages be appended to the message</li>
 * <li>getAttachmentType(), what should be attached to the message</li>
 * <li>getInLineType(), what should be included in the message</li>
 * <li>getMessage(), The text of the message itself</li>
 * <li>getRecipients(), the recipients the mail is sent to</li>
 * <li>getReplyTo(), where replies to this message will be sent</li>
 * <li>getReversePath(), what to set the reverse-path to</li>
 * <li>getSender(), who the mail is from</li>
 * <li>getSubject(), a string to replace the message subject</li>
 * <li>getSubjectPrefix(), a prefix to be added to the message subject, possibly
 * already replaced by a new subject</li>
 * <li>getTo(), a list of people to whom the mail is *apparently* sent</li>
 * <li>isReply(), should this mailet set the IN_REPLY_TO header to the id of the
 * current message</li>
 * <li>getPassThrough(), should this mailet allow the original message to
 * continue processing or GHOST it.</li>
 * <li>getFakeDomainCheck(), should this mailet check if the sender domain
 * address is valid.</li>
 * <li>isStatic(), should this mailet run the get methods for every mail, or
 * just once.</li>
 * </ul>
 * <p>
 * For each of the methods above (generically called "getX()" methods in this
 * class and its subclasses), there is an associated "getX(Mail)" method and
 * most times a "setX(Mail, Tx, Mail)" method.<br>
 * The roles are the following:
 * </p>
 * <ul>
 * <li>a "getX()" method returns the correspondent "X" value that can be
 * evaluated "statically" once at init time and then stored in a variable and
 * made available for later use by a "getX(Mail)" method;</li>
 * <li>a "getX(Mail)" method is the one called to return the correspondent "X"
 * value that can be evaluated "dynamically", tipically based on the currently
 * serviced mail; the default behaviour is to return the value of getX();</li>
 * <li>a "setX(Mail, Tx, Mail)" method is called to change the correspondent "X"
 * value of the redirected Mail object, using the value returned by
 * "gexX(Mail)"; if such value is null, it does nothing.</li>
 * </ul>
 * <p>
 * Here follows the typical pattern of those methods:
 * </p>
 * <p/>
 * <pre>
 * <code>
 *    ...
 *    Tx x;
 *    ...
 *    protected boolean getX(Mail originalMail) throws MessagingException {
 *        boolean x = (isStatic()) ? this.x : getX();
 *        ...
 *        return x;
 *    }
 *    ...
 *    public void init() throws MessagingException {
 *        ...
 *        isStatic = (getInitParameter("static") == null) ? false : new Boolean(getInitParameter("static")).booleanValue();
 *        if(isStatic()) {
 *            ...
 *            X  = getX();
 *            ...
 *        }
 *    ...
 *    public void service(Mail originalMail) throws MessagingException {
 *    ...
 *    setX(newMail, getX(originalMail), originalMail);
 *    ...
 *    }
 *    ...
 * </code>
 * </pre>
 * <p>
 * The <i>isStatic</i> variable and method is used to allow for the situations
 * (deprecated since version 2.2, but possibly used by previoulsy written
 * extensions to {@link Redirect}) in which the getX() methods are non static:
 * in this case {@link #isStatic()} must return false.<br>
 * Finally, a "getX()" method may return a "special address" (see
 * {@link SpecialAddress}), that later will be resolved ("late bound") by a
 * "getX(Mail)" or "setX(Mail, Tx, Mail)": it is a dynamic value that does not
 * require <code>isStatic</code> to be false.
 * </p>
 * <p/>
 * <p>
 * Supports by default the <code>passThrough</code> init parameter (false if
 * missing). Subclasses can override this behaviour overriding
 * {@link #getPassThrough()}.
 * </p>
 *
 * @since 2.2.0
 */

public abstract class AbstractRedirect extends GenericMailet {

    private static final char LINE_BREAK = '\n';

    protected abstract boolean isNotifyMailet();

    protected abstract String[] getAllowedInitParameters();

    protected boolean isDebug = false;

    protected boolean isStatic = false;

    private static enum SpecialAddressKind {
        SENDER("sender"),
        REVERSE_PATH("reverse.path"),
        FROM("from"),
        REPLY_TO("reply.to"),
        TO("to"),
        RECIPIENTS("recipients"),
        DELETE("delete"),
        UNALTERED("unaltered"),
        NULL("null");

        private String value;

        private SpecialAddressKind(String value) {
            this.value = value;
        }

        public static SpecialAddressKind forValue(String value) {
            for (SpecialAddressKind kind : values()) {
                if (kind.value.equals(value)) {
                    return kind;
                }
            }
            return null;
        }

        public String getValue() {
            return value;
        }
    }
    private static class AddressMarker {
        public static final String ADDRESS_MARKER = "address.marker";
        public static final MailAddress SENDER = mailAddressUncheckedException(SpecialAddressKind.SENDER, ADDRESS_MARKER);
        public static final MailAddress REVERSE_PATH = mailAddressUncheckedException(SpecialAddressKind.REVERSE_PATH, ADDRESS_MARKER);
        public static final MailAddress FROM = mailAddressUncheckedException(SpecialAddressKind.FROM, ADDRESS_MARKER);
        public static final MailAddress REPLY_TO = mailAddressUncheckedException(SpecialAddressKind.REPLY_TO, ADDRESS_MARKER);
        public static final MailAddress TO = mailAddressUncheckedException(SpecialAddressKind.TO, ADDRESS_MARKER);
        public static final MailAddress RECIPIENTS = mailAddressUncheckedException(SpecialAddressKind.RECIPIENTS, ADDRESS_MARKER);
        public static final MailAddress DELETE = mailAddressUncheckedException(SpecialAddressKind.DELETE, ADDRESS_MARKER);
        public static final MailAddress UNALTERED = mailAddressUncheckedException(SpecialAddressKind.UNALTERED, ADDRESS_MARKER);
        public static final MailAddress NULL = mailAddressUncheckedException(SpecialAddressKind.NULL, ADDRESS_MARKER);

        private static MailAddress mailAddressUncheckedException(SpecialAddressKind kind, String domain) {
            try {
                return new MailAddress(kind.getValue(), domain);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Class containing "special addresses" constants. Such addresses mean
     * dynamic values that later will be resolved ("late bound") by a
     * "getX(Mail)" or "setX(Mail, Tx, Mail)".
     */
    public static class SpecialAddress {
        public static final MailAddress SENDER = AddressMarker.SENDER;
        public static final MailAddress REVERSE_PATH = AddressMarker.REVERSE_PATH;
        public static final MailAddress FROM = AddressMarker.FROM;
        public static final MailAddress REPLY_TO = AddressMarker.REPLY_TO;
        public static final MailAddress TO = AddressMarker.TO;
        public static final MailAddress RECIPIENTS = AddressMarker.RECIPIENTS;
        public static final MailAddress DELETE = AddressMarker.DELETE;
        public static final MailAddress UNALTERED = AddressMarker.UNALTERED;
        public static final MailAddress NULL = AddressMarker.NULL;
    }

    // The values that indicate how to attach the original mail
    // to the new mail.

    private boolean passThrough = false;
    private boolean fakeDomainCheck = true;
    private TypeCode attachmentType = TypeCode.NONE;
    private TypeCode inLineType = TypeCode.BODY;
    private String messageText;
    private Collection<MailAddress> recipients;
    private MailAddress replyTo;
    private MailAddress reversePath;
    private MailAddress sender;
    private String subject;
    private String subjectPrefix;
    private InternetAddress[] apparentlyTo;
    private boolean attachError = false;
    private boolean isReply = false;

    protected DNSService dns;

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    /**
     * <p>
     * Gets the <code>static</code> property.
     * </p>
     * <p>
     * Return true to reduce calls to getTo, getSender, getRecipients,
     * getReplyTo, getReversePath amd getMessage where these values don't change
     * (eg hard coded, or got at startup from the mailet config); return false
     * where any of these methods generate their results dynamically eg in
     * response to the message being processed, or by reference to a repository
     * of users.
     * </p>
     * <p>
     * It is now (from version 2.2) somehow obsolete, as should be always true
     * because the "good practice" is to use "getX()" methods statically, and
     * use instead "getX(Mail)" methods for dynamic situations. A false value is
     * now meaningful only for subclasses of {@link Redirect} older than version
     * 2.2 that were relying on this.
     * </p>
     * <p/>
     * <p>
     * Is a "getX()" method.
     * </p>
     *
     * @return true, as normally "getX()" methods shouls be static
     */
    protected boolean isStatic() {
        return true;
    }

    /**
     * Gets the <code>passThrough</code> property. Return true to allow the
     * original message to continue through the processor, false to GHOST it. Is
     * a "getX()" method.
     *
     * @return the <code>passThrough</code> init parameter, or false if missing
     */
    protected boolean getPassThrough() {
        if (isNotifyMailet()) {
            return getInitParameter("passThrough", true);
        }
        return getInitParameter("passThrough", false);
    }

    /**
     * Gets the <code>passThrough</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getPassThrough()}
     */
    protected boolean getPassThrough(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.passThrough : getPassThrough();
    }

    /**
     * Gets the <code>fakeDomainCheck</code> property. Return true to check if
     * the sender domain is valid. Is a "getX()" method.
     *
     * @return the <code>fakeDomainCheck</code> init parameter, or true if
     *         missing
     */
    protected boolean getFakeDomainCheck() {
        return getInitParameter("fakeDomainCheck", false);
    }

    /**
     * Gets the <code>fakeDomainCheck</code> property, built dynamically using
     * the original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getFakeDomainCheck()}
     */
    protected boolean getFakeDomainCheck(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.fakeDomainCheck : getFakeDomainCheck();
    }

    /**
     * Gets the <code>inline</code> property. May return one of the following
     * values to indicate how to append the original message to build the new
     * message:
     * <ul>
     * <li><code>UNALTERED</code> : original message is the new message body</li>
     * <li><code>BODY</code> : original message body is appended to the new
     * message</li>
     * <li><code>HEADS</code> : original message headers are appended to the new
     * message</li>
     * <li><code>ALL</code> : original is appended with all headers</li>
     * <li><code>NONE</code> : original is not appended</li>
     * </ul>
     * Is a "getX()" method.
     *
     * @return the <code>inline</code> init parameter, or <code>UNALTERED</code>
     *         if missing
     */
    protected TypeCode getInLineType() {
        if (isNotifyMailet()) {
            return TypeCode.from(getInitParameter("inline", "none"));
        }
        return TypeCode.from(getInitParameter("inline", "unaltered"));
    }

    /**
     * Gets the <code>inline</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getInLineType()}
     */
    protected TypeCode getInLineType(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.inLineType : getInLineType();
    }

    /**
     * Gets the <code>attachment</code> property. May return one of the
     * following values to indicate how to attach the original message to the
     * new message:
     * <ul>
     * <li><code>BODY</code> : original message body is attached as plain text
     * to the new message</li>
     * <li><code>HEADS</code> : original message headers are attached as plain
     * text to the new message</li>
     * <li><code>ALL</code> : original is attached as plain text with all
     * headers</li>
     * <li><code>MESSAGE</code> : original message is attached as type
     * message/rfc822, a complete mail message.</li>
     * <li><code>NONE</code> : original is not attached</li>
     * </ul>
     * Is a "getX()" method.
     *
     * @return the <code>attachment</code> init parameter, or <code>NONE</code>
     *         if missing
     */
    protected TypeCode getAttachmentType() {
        if (isNotifyMailet()) {
            return TypeCode.from(getInitParameter("attachment", "message"));
        }
        return TypeCode.from(getInitParameter("attachment", "none"));
    }

    /**
     * Gets the <code>attachment</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getAttachmentType()}
     */
    protected TypeCode getAttachmentType(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.attachmentType : getAttachmentType();
    }

    /**
     * Gets the <code>message</code> property. Returns a message to which the
     * original message can be attached/appended to build the new message. Is a
     * "getX()" method.
     *
     * @return the <code>message</code> init parameter or an empty string if
     *         missing
     */
    protected String getMessage() {
        if (isNotifyMailet()) {
            return getInitParameter("notice", getInitParameter("message", "We were unable to deliver the attached message because of an error in the mail server."));
        }
        return getInitParameter("message", "");
    }

    /**
     * Gets the <code>message</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getMessage()}
     */
    protected String getMessage(Mail originalMail) throws MessagingException {
        if (isNotifyMailet()) {
            return getMessageForNotifyMailets(originalMail);
        }
        return (isStatic()) ? this.messageText : getMessage();
    }

    private String getMessageForNotifyMailets(Mail originalMail) throws MessagingException {
        MimeMessage message = originalMail.getMessage();
        StringBuffer buffer = new StringBuffer();

        buffer.append(getMessage()).append(LINE_BREAK);
        if (originalMail.getErrorMessage() != null) {
            buffer.append(LINE_BREAK)
                .append("Error message below:")
                .append(LINE_BREAK)
                .append(originalMail.getErrorMessage())
                .append(LINE_BREAK);
        }
        buffer.append(LINE_BREAK)
            .append("Message details:")
            .append(LINE_BREAK);

        if (message.getSubject() != null) {
            buffer.append("  Subject: " + message.getSubject())
                .append(LINE_BREAK);
        }
        if (message.getSentDate() != null) {
            buffer.append("  Sent date: " + message.getSentDate())
                .append(LINE_BREAK);
        }
        buffer.append("  MAIL FROM: " + originalMail.getSender())
            .append(LINE_BREAK);

        boolean firstRecipient = true;
        for (MailAddress recipient : originalMail.getRecipients()) {
            if (firstRecipient) {
                buffer.append("  RCPT TO: " + recipient)
                    .append(LINE_BREAK);
                firstRecipient = false;
            } else {
                buffer.append("           " + recipient)
                    .append(LINE_BREAK);
            }
        }

        appendAddresses(buffer, "From", message.getHeader(RFC2822Headers.FROM));
        appendAddresses(buffer, "To", message.getHeader(RFC2822Headers.TO));
        appendAddresses(buffer, "CC", message.getHeader(RFC2822Headers.CC));

        buffer.append("  Size (in bytes): " + message.getSize())
            .append(LINE_BREAK);
        if (message.getLineCount() >= 0) {
            buffer.append("  Number of lines: " + message.getLineCount())
                .append(LINE_BREAK);
        }

        return buffer.toString();
    }

    private void appendAddresses(StringBuffer buffer, String title, String[] addresses) {
        if (addresses != null) {
            buffer.append("  " + title + ": ")
                .append(LINE_BREAK);
            for (String address : addresses) {
                buffer.append(address + " ")
                    .append(LINE_BREAK);
            }
            buffer.append(LINE_BREAK);
        }
    }

    /**
     * Gets the <code>recipients</code> property. Returns the collection of
     * recipients of the new message, or null if no change is requested. Is a
     * "getX()" method.
     *
     * @return the <code>recipients</code> init parameter or the postmaster
     *         address or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.FROM</code> or
     *         <code>SpecialAddress.REPLY_TO</code> or
     *         <code>SpecialAddress.REVERSE_PATH</code> or
     *         <code>SpecialAddress.UNALTERED</code> or
     *         <code>SpecialAddress.RECIPIENTS</code> or <code>null</code> if
     *         missing
     */
    protected Collection<MailAddress> getRecipients() throws MessagingException {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        String[] allowedSpecials = new String[] { "postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null" };
        for (InternetAddress address : extractAddresses(getAddressesFromParameter("recipients"))) {
            builder.add(toMailAddress(address, allowedSpecials));
        }
        return builder.build();
    }

    private InternetAddress[] extractAddresses(String addressList) throws MessagingException {
        try {
            return InternetAddress.parse(addressList, false);
        } catch (AddressException e) {
            throw new MessagingException("Exception thrown parsing: " + addressList, e);
        }
    }

    private String getAddressesFromParameter(String parameter) throws MessagingException {
        String recipients = getInitParameter(parameter);
        if (Strings.isNullOrEmpty(recipients)) {
            return null;
        }
        return recipients;
    }

    private MailAddress toMailAddress(InternetAddress address, String[] allowedSpecials) throws MessagingException {
        try {
            MailAddress specialAddress = getSpecialAddress(address.getAddress(), allowedSpecials);
            if (specialAddress != null) {
                return specialAddress;
            }
            return new MailAddress(address);
        } catch (Exception e) {
            throw new MessagingException("Exception thrown parsing: " + address.getAddress());
        }
    }

    /**
     * Gets the <code>recipients</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #replaceMailAddresses} on {@link #getRecipients()},
     */
    protected Collection<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        Collection<MailAddress> recipients = (isStatic()) ? this.recipients : getRecipients();
        if (recipients != null) {
            if (containsOnlyUnalteredOrRecipients(recipients)) {
                return null;
            }
            return replaceMailAddresses(originalMail, recipients);
        }
        return null;
    }

    private boolean containsOnlyUnalteredOrRecipients(Collection<MailAddress> recipients) {
        return recipients.size() == 1 && 
                (recipients.contains(SpecialAddress.UNALTERED) || recipients.contains(SpecialAddress.RECIPIENTS));
    }

    /**
     * Sets the recipients of <i>newMail</i> to <i>recipients</i>. If the
     * requested value is null does nothing. Is a "setX(Mail, Tx, Mail)" method.
     */
    protected void setRecipients(Mail newMail, Collection<MailAddress> recipients, Mail originalMail) {
        if (recipients != null) {
            newMail.setRecipients(recipients);
            if (isDebug) {
                log("recipients set to: " + arrayToString(recipients.toArray()));
            }
        }
    }

    /**
     * Gets the <code>to</code> property. Returns the "To:" recipients of the
     * new message. or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>to</code> init parameter or the postmaster address or
     *         <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.REVERSE_PATH</code> or
     *         <code>SpecialAddress.FROM</code> or
     *         <code>SpecialAddress.REPLY_TO</code> or
     *         <code>SpecialAddress.UNALTERED</code> or
     *         <code>SpecialAddress.TO</code> or <code>null</code> if missing
     */
    protected InternetAddress[] getTo() throws MessagingException {
        if (isNotifyMailet()) {
            return null;
        }
        ImmutableList.Builder<InternetAddress> builder = ImmutableList.builder();
        String[] allowedSpecials = new String[] { "postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null" };
        for (InternetAddress address : extractAddresses(getAddressesFromParameter("to"))) {
            builder.add(toMailAddress(address, allowedSpecials).toInternetAddress());
        }
        ImmutableList<InternetAddress> addresses = builder.build();
        return addresses.toArray(new InternetAddress[addresses.size()]);
    }

    /**
     * Gets the <code>to</code> property, built dynamically using the original
     * Mail object. Its outcome will be the the value the <i>TO:</i> header will
     * be set to, that could be different from the real recipient (see
     * {@link Mail#getRecipients}). Is a "getX(Mail)" method.
     *
     * @return {@link #replaceInternetAddresses} on {@link #getRecipients()},
     */
    protected InternetAddress[] getTo(Mail originalMail) throws MessagingException {
        InternetAddress[] apparentlyTo = (isStatic()) ? this.apparentlyTo : getTo();
        if (apparentlyTo != null) {
            if (containsOnlyUnalteredOrTo(apparentlyTo)) {
                return null;
            }
            Collection<InternetAddress> addresses = replaceInternetAddresses(originalMail, ImmutableList.copyOf(apparentlyTo));
            return addresses.toArray(new InternetAddress[addresses.size()]);
        }

        return null;
    }

    private boolean containsOnlyUnalteredOrTo(InternetAddress[] to) {
        return to.length == 1 && 
                (to[0].equals(SpecialAddress.UNALTERED.toInternetAddress()) || to[0].equals(SpecialAddress.RECIPIENTS.toInternetAddress()));
    }

    /**
     * Sets the "To:" header of <i>newMail</i> to <i>to</i>. If the requested
     * value is null does nothing. Is a "setX(Mail, Tx, Mail)" method.
     */
    protected void setTo(Mail newMail, InternetAddress[] to, Mail originalMail) throws MessagingException {
        if (to != null) {
            newMail.getMessage().setRecipients(Message.RecipientType.TO, to);
            if (isDebug) {
                log("apparentlyTo set to: " + arrayToString(to));
            }
        }
    }

    /**
     * Gets the <code>replyto</code> property. Returns the Reply-To address of
     * the new message, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>replyto</code> init parameter or the postmaster address
     *         or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.UNALTERED</code> or
     *         <code>SpecialAddress.NULL</code> or <code>null</code> if missing
     */
    protected MailAddress getReplyTo() throws MessagingException {
        if (isNotifyMailet()) {
            return SpecialAddress.NULL;
        }

        String replyTo = getAddressesFromParameterWithReplacementParameter("replyTo", "replyto");
        if (Strings.isNullOrEmpty(replyTo)) {
            return null;
        }

        InternetAddress[] extractAddresses = extractAddresses(replyTo);
        if (extractAddresses == null || extractAddresses.length == 0) {
            return null;
        }
        return toMailAddress(extractAddresses[0], new String[] { "postmaster", "sender", "null", "unaltered" });
    }

    private String getAddressesFromParameterWithReplacementParameter(String parameter, String replacementParameter) throws MessagingException {
        String recipients = getInitParameter(parameter, getInitParameter(replacementParameter));
        if (Strings.isNullOrEmpty(recipients)) {
            return null;
        }
        return recipients;
    }

    /**
     * Gets the <code>replyTo</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getReplyTo()} replacing
     *         <code>SpecialAddress.UNALTERED</code> if applicable with null and
     *         <code>SpecialAddress.SENDER</code> with the original mail sender
     */
    protected MailAddress getReplyTo(Mail originalMail) throws MessagingException {
        MailAddress replyTo = (isStatic()) ? this.replyTo : getReplyTo();
        if (replyTo != null) {
            if (replyTo.equals(SpecialAddress.UNALTERED)) {
                return null;
            }
            return originalMail.getSender();
        }
        return null;
    }

    /**
     * <p>
     * Sets the "Reply-To:" header of <i>newMail</i> to <i>replyTo</i>.
     * </p>
     * If the requested value is <code>SpecialAddress.NULL</code> will remove
     * the "Reply-To:" header. If the requested value is null does nothing.</p>
     * Is a "setX(Mail, Tx, Mail)" method.
     */
    protected void setReplyTo(Mail newMail, MailAddress replyTo, Mail originalMail) throws MessagingException {
        if (replyTo != null) {
            if (replyTo.equals(SpecialAddress.NULL)) {
                newMail.getMessage().setReplyTo(null);
                if (isDebug) {
                    log("replyTo set to: null");
                }
            } else {
                newMail.getMessage().setReplyTo(new InternetAddress[] { replyTo.toInternetAddress() });
                if (isDebug) {
                    log("replyTo set to: " + replyTo);
                }
            }
        }
    }

    /**
     * Gets the <code>reversePath</code> property. Returns the reverse-path of
     * the new message, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>reversePath</code> init parameter or the postmaster
     *         address or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.NULL</code> or
     *         <code>SpecialAddress.UNALTERED</code> or <code>null</code> if
     *         missing
     */
    protected MailAddress getReversePath() throws MessagingException {
        String reversePath = getAddressesFromParameter("reversePath");
        if (Strings.isNullOrEmpty(reversePath)) {
            return null;
        }

        InternetAddress[] extractAddresses = extractAddresses(reversePath);
        if (extractAddresses == null || extractAddresses.length == 0) {
            return null;
        }
        return toMailAddress(extractAddresses[0], new String[] { "postmaster", "sender", "null", "unaltered" });
    }

    /**
     * Gets the <code>reversePath</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getReversePath()}, replacing
     *         <code>SpecialAddress.SENDER</code> if applicable with null,
     *         replacing <code>SpecialAddress.UNALTERED</code> and
     *         <code>SpecialAddress.REVERSE_PATH</code> if applicable with null,
     *         but not replacing <code>SpecialAddress.NULL</code> that will be
     *         handled by {@link #setReversePath}
     */
    protected MailAddress getReversePath(Mail originalMail) throws MessagingException {
        if (isNotifyMailet()) {
            return getSender(originalMail);
        }

        MailAddress reversePath = (isStatic()) ? this.reversePath : getReversePath();
        if (reversePath != null) {
            if (isUnalteredOrReversePathOrSender(reversePath)) {
                return null;
            }
        }
        return reversePath;
    }

    private boolean isUnalteredOrReversePathOrSender(MailAddress reversePath) {
        return reversePath.equals(SpecialAddress.UNALTERED)
                || reversePath.equals(SpecialAddress.REVERSE_PATH)
                || reversePath.equals(SpecialAddress.SENDER);
    }

    /**
     * Sets the "reverse-path" of <i>newMail</i> to <i>reversePath</i>. If the
     * requested value is <code>SpecialAddress.NULL</code> sets it to "<>". If
     * the requested value is null does nothing. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    protected void setReversePath(MailImpl newMail, MailAddress reversePath, Mail originalMail) {
        if (reversePath != null) {
            if (reversePath.equals(SpecialAddress.NULL)) {
                newMail.setSender(null);
                if (isDebug) {
                    log("reversePath set to: null");
                }
            } else {
                newMail.setSender(reversePath);
                if (isDebug) {
                    log("reversePath set to: " + reversePath);
                }
            }
        }
    }

    /**
     * Gets the <code>sender</code> property. Returns the new sender as a
     * MailAddress, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>sender</code> init parameter or the postmaster address
     *         or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.UNALTERED</code> or <code>null</code> if
     *         missing
     */
    protected MailAddress getSender() throws MessagingException {
        String sender = getAddressesFromParameter("sender");
        if (Strings.isNullOrEmpty(sender)) {
            return null;
        }

        InternetAddress[] extractAddresses = extractAddresses(sender);
        if (extractAddresses == null || extractAddresses.length == 0) {
            return null;
        }
        return toMailAddress(extractAddresses[0], new String[] { "postmaster", "sender", "unaltered" });
    }

    /**
     * Gets the <code>sender</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getSender()} replacing
     *         <code>SpecialAddress.UNALTERED</code> and
     *         <code>SpecialAddress.SENDER</code> if applicable with null
     */
    protected MailAddress getSender(Mail originalMail) throws MessagingException {
        MailAddress sender = (isStatic()) ? this.sender : getSender();
        if (sender != null) {
            if (isUnalteredOrSender(sender)) {
                return null;
            }
        }
        return sender;
    }

    private boolean isUnalteredOrSender(MailAddress sender) {
        return sender.equals(SpecialAddress.UNALTERED) || sender.equals(SpecialAddress.SENDER);
    }

    /**
     * Sets the "From:" header of <i>newMail</i> to <i>sender</i>. If the
     * requested value is null does nothing. Is a "setX(Mail, Tx, Mail)" method.
     */
    protected void setSender(Mail newMail, MailAddress sender, Mail originalMail) throws MessagingException {
        if (sender != null) {
            newMail.getMessage().setFrom(sender.toInternetAddress());

            if (isDebug) {
                log("sender set to: " + sender);
            }
        }
    }

    /**
     * Gets the <code>subject</code> property. Returns a string for the new
     * message subject. Is a "getX()" method.
     *
     * @return the <code>subject</code> init parameter or null if missing
     */
    protected String getSubject() {
        if (isNotifyMailet()) {
            return null;
        }
        return getInitParameter("subject");
    }

    /**
     * Gets the <code>subject</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getSubject()}
     */
    protected String getSubject(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.subject : getSubject();
    }

    /**
     * Gets the <code>prefix</code> property. Returns a prefix for the new
     * message subject. Is a "getX()" method.
     *
     * @return the <code>prefix</code> init parameter or an empty string if
     *         missing
     */
    protected String getSubjectPrefix() {
        if (isNotifyMailet()) {
            return getInitParameter("prefix", "Re:");
        }
        return getInitParameter("prefix");
    }

    /**
     * Gets the <code>subjectPrefix</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getSubjectPrefix()}
     */
    protected String getSubjectPrefix(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.subjectPrefix : getSubjectPrefix();
    }

    /**
     * Builds the subject of <i>newMail</i> appending the subject of
     * <i>originalMail</i> to <i>subjectPrefix</i>. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    protected void setSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        if (isNotifyMailet()) {
            String subject = Strings.nullToEmpty(originalMail.getMessage().getSubject());
            if (subjectPrefix == null || !subject.contains(subjectPrefix)) {
                newMail.getMessage().setSubject(subject);
            } else {
                newMail.getMessage().setSubject(subjectPrefix + subject);
            }
        }

        String subject = getSubject(originalMail);
        if (!Strings.isNullOrEmpty(subjectPrefix) || subject != null) {
            String newSubject = Strings.nullToEmpty(subject);
            if (subject == null) {
                newSubject = Strings.nullToEmpty(originalMail.getMessage().getSubject());
            } else {
                if (isDebug) {
                    log("subject set to: " + subject);
                }
            }

            if (subjectPrefix != null) {
                newSubject = subjectPrefix + newSubject;
                if (isDebug) {
                    log("subjectPrefix set to: " + subjectPrefix);
                }
            }
            changeSubject(newMail.getMessage(), newSubject);
        }
    }

    /**
     * Gets the <code>attachError</code> property. Returns a boolean indicating
     * whether to append a description of any error to the main body part of the
     * new message, if getInlineType does not return "UNALTERED". Is a "getX()"
     * method.
     *
     * @return the <code>attachError</code> init parameter; false if missing
     */
    protected boolean attachError() throws MessagingException {
        return getInitParameter("attachError", false);
    }

    /**
     * Gets the <code>attachError</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #attachError()}
     */
    protected boolean attachError(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.attachError : attachError();
    }

    /**
     * Gets the <code>isReply</code> property. Returns a boolean indicating
     * whether the new message must be considered a reply to the original
     * message, setting the IN_REPLY_TO header of the new message to the id of
     * the original message. Is a "getX()" method.
     *
     * @return the <code>isReply</code> init parameter; false if missing
     */
    protected boolean isReply() {
        if (isNotifyMailet()) {
            return true;
        }
        return getInitParameter("isReply", false);
    }

    /**
     * Gets the <code>isReply</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #isReply()}
     */
    protected boolean isReply(Mail originalMail) throws MessagingException {
        return (isStatic()) ? this.isReply : isReply();
    }

    /**
     * Sets the "In-Reply-To:" header of <i>newMail</i> to the "Message-Id:" of
     * <i>originalMail</i>, if <i>isReply</i> is true.
     */
    protected void setIsReply(Mail newMail, boolean isReply, Mail originalMail) throws MessagingException {
        if (isReply) {
            String messageId = originalMail.getMessage().getMessageID();
            if (messageId != null) {
                newMail.getMessage().setHeader(RFC2822Headers.IN_REPLY_TO, messageId);
                if (isDebug) {
                    log("IN_REPLY_TO set to: " + messageId);
                }
            }
        }
    }

    /**
     * Mailet initialization routine. Will setup static values for each "x"
     * initialization parameter in config.xml, using getX(), if
     * {@link #isStatic()} returns true.
     */
    @Override
    public void init() throws MessagingException {
        isDebug = getInitParameter("debug", false);
        isStatic = getInitParameter("static", false);

        if (isDebug) {
            log("Initializing");
        }

        // check that all init parameters have been declared in
        // allowedInitParameters
        checkInitParameters(getAllowedInitParameters());

        if (isStatic()) {
            passThrough = getPassThrough();
            fakeDomainCheck = getFakeDomainCheck();
            attachmentType = getAttachmentType();
            inLineType = getInLineType();
            messageText = getMessage();
            recipients = getRecipients();
            replyTo = getReplyTo();
            reversePath = getReversePath();
            sender = getSender();
            subject = getSubject();
            subjectPrefix = getSubjectPrefix();
            apparentlyTo = getTo();
            attachError = attachError();
            isReply = isReply();
            if (isDebug) {
                String logBuffer = "static" + ", passThrough=" + passThrough + ", fakeDomainCheck=" + fakeDomainCheck + ", sender=" + sender + ", replyTo=" + replyTo + ", reversePath=" + reversePath + ", message=" + messageText + ", recipients=" + arrayToString(recipients == null ? null : recipients.toArray()) + ", subject=" + subject + ", subjectPrefix=" + subjectPrefix + ", apparentlyTo=" + arrayToString(apparentlyTo) + ", attachError=" + attachError + ", isReply=" + isReply + ", attachmentType=" + attachmentType + ", inLineType=" + inLineType + " ";
                log(logBuffer);
            }
        }
    }

    /**
     * Service does the hard work,and redirects the originalMail in the form
     * specified.
     *
     * @param originalMail the mail to process and redirect
     * @throws MessagingException if a problem arises formulating the redirected mail
     */
    @Override
    public void service(Mail originalMail) throws MessagingException {

        boolean keepMessageId = false;

        // duplicates the Mail object, to be able to modify the new mail keeping
        // the original untouched
        MailImpl newMail = new MailImpl(originalMail);
        try {
            setRemoteAddr(newMail);
            setRemoteHost(newMail);

            if (isDebug) {
                log("New mail - sender: " + newMail.getSender() + ", recipients: " + arrayToString(newMail.getRecipients().toArray()) + ", name: " + newMail.getName() + ", remoteHost: " + newMail.getRemoteHost() + ", remoteAddr: " + newMail.getRemoteAddr() + ", state: " + newMail.getState()
                        + ", lastUpdated: " + newMail.getLastUpdated() + ", errorMessage: " + newMail.getErrorMessage());
            }

            // Create the message
            if (!getInLineType(originalMail).equals(TypeCode.UNALTERED)) {
                if (isDebug) {
                    log("Alter message");
                }
                newMail.setMessage(new MimeMessage(Session.getDefaultInstance(System.getProperties(), null)));

                // handle the new message if altered
                buildAlteredMessage(newMail, originalMail);

            } else {
                // if we need the original, create a copy of this message to
                // redirect
                if (getPassThrough(originalMail)) {
                    newMail.setMessage(new MimeMessage(originalMail.getMessage()) {
                        protected void updateHeaders() throws MessagingException {
                            if (getMessageID() == null)
                                super.updateHeaders();
                            else {
                                modified = false;
                            }
                        }
                    });
                }
                if (isDebug) {
                    log("Message resent unaltered.");
                }
                keepMessageId = true;
            }

            // Set additional headers

            setRecipients(newMail, getRecipients(originalMail), originalMail);

            setTo(newMail, getTo(originalMail), originalMail);

            setSubjectPrefix(newMail, getSubjectPrefix(originalMail), originalMail);

            if (newMail.getMessage().getHeader(RFC2822Headers.DATE) == null) {
                newMail.getMessage().setHeader(RFC2822Headers.DATE, DateFormats.RFC822_DATE_FORMAT.format(new Date()));
            }

            setReplyTo(newMail, getReplyTo(originalMail), originalMail);

            setReversePath(newMail, getReversePath(originalMail), originalMail);

            setSender(newMail, getSender(originalMail), originalMail);

            setIsReply(newMail, isReply(originalMail), originalMail);

            newMail.getMessage().saveChanges();
            newMail.removeAllAttributes();

            if (keepMessageId) {
                setMessageId(newMail, originalMail);
            }

            if (senderDomainIsValid(newMail)) {
                // Send it off...
                getMailetContext().sendMail(newMail);
            } else {
                String logBuffer = getMailetName() + " mailet cannot forward " + originalMail.getName() + ". Invalid sender domain for " + newMail.getSender() + ". Consider using the Resend mailet " + "using a different sender.";
                throw new MessagingException(logBuffer);
            }

        } finally {
            newMail.dispose();
        }

        if (!getPassThrough(originalMail)) {
            originalMail.setState(Mail.GHOST);
        }
    }

    private void setRemoteAddr(MailImpl newMail) {
        try {
            newMail.setRemoteAddr(dns.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            newMail.setRemoteAddr("127.0.0.1");
        }
    }

    private void setRemoteHost(MailImpl newMail) {
        try {
            newMail.setRemoteHost(dns.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            newMail.setRemoteHost("localhost");
        }
    }

    /**
     * Utility method for obtaining a string representation of a Message's
     * headers
     */
    protected String getMessageHeaders(MimeMessage message) throws MessagingException {
        @SuppressWarnings("unchecked")
        Enumeration<String> heads = message.getAllHeaderLines();
        StringBuilder headBuffer = new StringBuilder(1024);
        while (heads.hasMoreElements()) {
            headBuffer.append(heads.nextElement().toString()).append("\r\n");
        }
        return headBuffer.toString();
    }

    /**
     * Utility method for obtaining a string representation of a Message's body
     */
    private String getMessageBody(MimeMessage message) throws Exception {
        ByteArrayOutputStream bodyOs = new ByteArrayOutputStream();
        MimeMessageUtil.writeMessageBodyTo(message, bodyOs);
        return bodyOs.toString();
    }

    /**
     * Builds the message of the newMail in case it has to be altered.
     *
     * @param originalMail the original Mail object
     * @param newMail      the Mail object to build
     */
    protected void buildAlteredMessage(Mail newMail, Mail originalMail) throws MessagingException {

        MimeMessage originalMessage = originalMail.getMessage();
        MimeMessage newMessage = newMail.getMessage();

        // Copy the relevant headers
        copyRelevantHeaders(originalMessage, newMessage);

        String head = getMessageHeaders(originalMessage);
        try {
            // Create the message body
            MimeMultipart multipart = new MimeMultipart("mixed");

            // Create the message
            MimeMultipart mpContent = new MimeMultipart("alternative");
            mpContent.addBodyPart(getBodyPart(originalMail, originalMessage, head));

            MimeBodyPart contentPartRoot = new MimeBodyPart();
            contentPartRoot.setContent(mpContent);

            multipart.addBodyPart(contentPartRoot);

            if (isDebug) {
                log("attachmentType:" + getAttachmentType(originalMail));
            }
            if (!getAttachmentType(originalMail).equals(TypeCode.NONE)) {
                multipart.addBodyPart(getAttachmentPart(originalMail, originalMessage, head));
            }

            if (attachError(originalMail) && originalMail.getErrorMessage() != null) {
                multipart.addBodyPart(getErrorPart(originalMail));
            }
            newMail.getMessage().setContent(multipart);
            newMail.getMessage().setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());

        } catch (Exception ioe) {
            throw new MessagingException("Unable to create multipart body", ioe);
        }
    }

    private BodyPart getBodyPart(Mail originalMail, MimeMessage originalMessage, String head) throws MessagingException, Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(getText(originalMail, originalMessage, head));
        part.setDisposition("inline");
        return part;
    }

    private MimeBodyPart getAttachmentPart(Mail originalMail, MimeMessage originalMessage, String head) throws MessagingException, Exception {
        MimeBodyPart attachmentPart = new MimeBodyPart();
        switch (getAttachmentType(originalMail)) {
            case HEADS:
                attachmentPart.setText(head);
                break;
            case BODY:
                try {
                    attachmentPart.setText(getMessageBody(originalMessage));
                } catch (Exception e) {
                    attachmentPart.setText("body unavailable");
                }
                break;
            case ALL:
                attachmentPart.setText(head + "\r\nMessage:\r\n" + getMessageBody(originalMessage));
                break;
            case MESSAGE:
                attachmentPart.setContent(originalMessage, "message/rfc822");
                break;
            case NONE:
                break;
            case UNALTERED:
                break;
        }
        if ((originalMessage.getSubject() != null) && (originalMessage.getSubject().trim().length() > 0)) {
            attachmentPart.setFileName(originalMessage.getSubject().trim());
        } else {
            attachmentPart.setFileName("No Subject");
        }
        attachmentPart.setDisposition("Attachment");
        return attachmentPart;
    }

    private MimeBodyPart getErrorPart(Mail originalMail) throws MessagingException {
        MimeBodyPart errorPart = new MimeBodyPart();
        errorPart.setContent(originalMail.getErrorMessage(), "text/plain");
        errorPart.setHeader(RFC2822Headers.CONTENT_TYPE, "text/plain");
        errorPart.setFileName("Reasons");
        errorPart.setDisposition(javax.mail.Part.ATTACHMENT);
        return errorPart;
    }

    private String getText(Mail originalMail, MimeMessage originalMessage, String head) throws MessagingException {
        StringBuilder builder = new StringBuilder();

        String messageText = getMessage(originalMail);
        if (messageText != null) {
            builder.append(messageText)
                .append(LINE_BREAK);
        }

        if (isDebug) {
            log("inline:" + getInLineType(originalMail));
        }
        boolean all = false;
        switch (getInLineType(originalMail)) {
            case ALL:
                all = true;
            case HEADS:
                builder.append("Message Headers:")
                    .append(LINE_BREAK)
                    .append(head)
                    .append(LINE_BREAK);
                if (!all) {
                    break;
                }
            case BODY:
                appendBody(builder, originalMessage);
                break;
            case NONE:
                break;
            case MESSAGE:
                break;
            case UNALTERED:
                break;
        }
        return builder.toString();
    }

    private void appendBody(StringBuilder builder, MimeMessage originalMessage) {
        builder.append("Message:")
            .append(LINE_BREAK);
        try {
            builder.append(getMessageBody(originalMessage))
                .append(LINE_BREAK);
        } catch (Exception e) {
            builder.append("body unavailable")
                .append(LINE_BREAK);
        }
    }

    private void copyRelevantHeaders(MimeMessage originalMessage, MimeMessage newMessage) throws MessagingException {
        @SuppressWarnings("unchecked")
        Enumeration<String> headerEnum = originalMessage.getMatchingHeaderLines(
                new String[] { RFC2822Headers.DATE, RFC2822Headers.FROM, RFC2822Headers.REPLY_TO, RFC2822Headers.TO, 
                        RFC2822Headers.SUBJECT, RFC2822Headers.RETURN_PATH });
        while (headerEnum.hasMoreElements()) {
            newMessage.addHeaderLine(headerEnum.nextElement());
        }
    }

    private void setMessageId(Mail newMail, Mail originalMail) throws MessagingException {
        String messageId = originalMail.getMessage().getMessageID();
        if (messageId != null) {
            newMail.getMessage().setHeader(RFC2822Headers.MESSAGE_ID, messageId);
            if (isDebug) {
                log("MESSAGE_ID restored to: " + messageId);
            }
        }
    }

    /**
     * Returns the {@link SpecialAddress} that corresponds to an init parameter
     * value. The init parameter value is checked against a String[] of allowed
     * values. The checks are case insensitive.
     *
     * @param addressString   the string to check if is a special address
     * @param allowedSpecials a String[] with the allowed special addresses
     * @return a SpecialAddress if found, null if not found or addressString is
     *         null
     * @throws MessagingException if is a special address not in the allowedSpecials array
     */
    protected final MailAddress getSpecialAddress(String addressString, String[] allowedSpecials) throws MessagingException {
        if (addressString == null) {
            return null;
        }

        MailAddress specialAddress = toMailAddress(addressString);
        if (specialAddress != null) {
            if (!isAllowed(addressString, allowedSpecials)) {
                throw new MessagingException("Special (\"magic\") address found not allowed: " + addressString + ", allowed values are \"" + arrayToString(allowedSpecials) + "\"");
            }
        }
        return specialAddress;
    }

    private MailAddress toMailAddress(String addressString) {
        String lowerCaseTrimed = addressString.toLowerCase(Locale.US).trim();
        if (lowerCaseTrimed.equals("postmaster")) {
            return getMailetContext().getPostmaster();
        }
        if (lowerCaseTrimed.equals("sender")) {
            return SpecialAddress.SENDER;
        }
        if (lowerCaseTrimed.equals("reversepath")) {
            return SpecialAddress.REVERSE_PATH;
        }
        if (lowerCaseTrimed.equals("from")) {
            return SpecialAddress.FROM;
        }
        if (lowerCaseTrimed.equals("replyto")) {
            return SpecialAddress.REPLY_TO;
        }
        if (lowerCaseTrimed.equals("to")) {
            return SpecialAddress.TO;
        }
        if (lowerCaseTrimed.equals("recipients")) {
            return SpecialAddress.RECIPIENTS;
        }
        if (lowerCaseTrimed.equals("delete")) {
            return SpecialAddress.DELETE;
        }
        if (lowerCaseTrimed.equals("unaltered")) {
            return SpecialAddress.UNALTERED;
        }
        if (lowerCaseTrimed.equals("null")) {
            return SpecialAddress.NULL;
        }
        return null;
    }

    private boolean isAllowed(String addressString, String[] allowedSpecials) {
        for (String allowedSpecial : allowedSpecials) {
            if (addressString.equals(allowedSpecial.toLowerCase(Locale.US).trim())) {
                return true;
            }
        }
        return false;
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
    protected final boolean senderDomainIsValid(Mail mail) throws MessagingException {
        return !getFakeDomainCheck(mail)
                || mail.getSender() == null
                || !getMailetContext().getMailServers(mail.getSender().getDomain()).isEmpty();
    }

    /**
     * It changes the subject of the supplied message to to supplied value but
     * it also tries to preserve the original charset information.<br>
     * <p/>
     * This method was needed to avoid sending the subject using a charset
     * (usually the default charset on the server) which doesn't contain the
     * characters in the subject, resulting in the loss of these characters. The
     * most simple method would be to either send it in ASCII unencoded or in
     * UTF-8 if non-ASCII characters are present but unfortunately UTF-8 is not
     * yet a MIME standard and not all email clients are supporting it. The
     * optimal method would be to determine the best charset by analyzing the
     * actual characters. That would require much more work (exept if an open
     * source library already exists for this). However there is nothing to stop
     * somebody to add a detection algorithm for a specific charset. <br>
     * <p/>
     * The current algorithm works correctly if only ASCII characters are added
     * to an existing subject.<br>
     * <p/>
     * If the new value is ASCII only, then it doesn't apply any encoding to the
     * subject header. (This is provided by MimeMessage.setSubject()).<br>
     * <p/>
     * Possible enhancement: under java 1.4 java.nio the system can determine if
     * the suggested charset fits or not (if there is untranslatable
     * characters). If the charset doesn't fit the new value, it can fall back
     * to UTF-8.<br>
     *
     * @param message  the message of which subject is changed
     * @param newValue the new (unencoded) value of the subject. It must not be null.
     * @throws MessagingException - according to the JavaMail doc most likely this is never
     *                            thrown
     */
    public void changeSubject(MimeMessage message, String newValue) throws MessagingException {
        String rawSubject = message.getHeader(RFC2822Headers.SUBJECT, null);
        String mimeCharset = determineMailHeaderEncodingCharset(rawSubject);
        if (mimeCharset == null) { // most likely ASCII
            // it uses the system charset or the value of the
            // mail.mime.charset property if set
            message.setSubject(newValue);
        } else { // original charset determined
            try {
                message.setSubject(newValue, MimeUtility.javaCharset(mimeCharset));
            } catch (MessagingException e) {
                // known, but unsupported encoding
                // this should be logged, the admin may setup a more i18n
                // capable JRE, but the log API cannot be accessed from here
                // if (charset != null) log(charset +
                // " charset unsupported by the JRE, email subject may be damaged");
                message.setSubject(newValue); // recover
            }
        }
    }

    /**
     * It attempts to determine the charset used to encode an "unstructured" RFC
     * 822 header (like Subject). The encoding is specified in RFC 2047. If it
     * cannot determine or the the text is not encoded then it returns null.
     * <p/>
     * Here is an example raw text: Subject:
     * =?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=
     *
     * @param rawText the raw (not decoded) value of the header. Null means that the
     *                header was not present (in this case it always return null).
     * @return the MIME charset name or null if no encoding applied
     */
    @VisibleForTesting String determineMailHeaderEncodingCharset(String rawText) {
        if (Strings.isNullOrEmpty(rawText)) {
            return null;
        }
        int iEncodingPrefix = rawText.indexOf("=?");
        if (iEncodingPrefix == -1) {
            return null;
        }
        int iCharsetBegin = iEncodingPrefix + 2;
        int iSecondQuestionMark = rawText.indexOf('?', iCharsetBegin);
        if (iSecondQuestionMark == -1) {
            return null;
        }
        if (iSecondQuestionMark == iCharsetBegin) {
            return null;
        }
        int iThirdQuestionMark = rawText.indexOf('?', iSecondQuestionMark + 1);
        if (iThirdQuestionMark == -1) {
            return null;
        }
        if (rawText.indexOf("?=", iThirdQuestionMark + 1) == -1) {
            return null;
        }
        return rawText.substring(iCharsetBegin, iSecondQuestionMark);
    }

    /**
     * Returns a new Collection built over <i>list</i> replacing special
     * addresses with real <code>MailAddress</code>-es.<br>
     * Manages <code>SpecialAddress.SENDER</code>,
     * <code>SpecialAddress.REVERSE_PATH</code>,
     * <code>SpecialAddress.FROM</code>, <code>SpecialAddress.REPLY_TO</code>,
     * <code>SpecialAddress.RECIPIENTS</code>, <code>SpecialAddress.TO</code>,
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code>.<br>
     * <code>SpecialAddress.FROM</code> is made equivalent to
     * <code>SpecialAddress.SENDER</code>; <code>SpecialAddress.TO</code> is
     * made equivalent to <code>SpecialAddress.RECIPIENTS</code>.<br>
     * <code>SpecialAddress.REPLY_TO</code> uses the ReplyTo header if
     * available, otherwise the From header if available, otherwise the Sender
     * header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code> are ignored.<br>
     * Any other address is not replaced.
     */
    protected Collection<MailAddress> replaceMailAddresses(Mail mail, Collection<MailAddress> list) {
        ImmutableSet.Builder<MailAddress> builder = ImmutableSet.builder();
        for (MailAddress mailAddress : list) {
            if (!isSpecialAddress(mailAddress)) {
                builder.add(mailAddress);
                continue;
            }

            SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
            if (specialAddressKind == null) {
                builder.add(mailAddress);
                continue;
            }
            switch (specialAddressKind) {
            case SENDER:
            case FROM:
                MailAddress sender = mail.getSender();
                if (sender != null) {
                    builder.add(sender);
                }
                break;
            case REPLY_TO:
                addReplyToFromMail(builder, mail);
                break;
            case REVERSE_PATH:
                MailAddress reversePath = mail.getSender();
                if (reversePath != null) {
                    builder.add(reversePath);
                }
                break;
            case RECIPIENTS:
            case TO:
                builder.addAll(mail.getRecipients());
                break;
            case UNALTERED:
            case NULL:
                break;
            case DELETE:
                builder.add(mailAddress);
                break;
            }
        }
        return builder.build();
    }

    private boolean isSpecialAddress(MailAddress mailAddress) {
        return mailAddress.getDomain().equalsIgnoreCase(AddressMarker.ADDRESS_MARKER);
    }

    private void addReplyToFromMail(ImmutableSet.Builder<MailAddress> set, Mail mail) {
        try {
            InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
            if (replyToArray == null || replyToArray.length == 0) {
                MailAddress sender = mail.getSender();
                if (sender != null) {
                    set.add(sender);
                }
            } else {
                addReplyTo(set, replyToArray);
            }
        } catch (MessagingException ae) {
            log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
        }
    }

    private void addReplyTo(ImmutableSet.Builder<MailAddress> set, InternetAddress[] replyToArray) {
        for (InternetAddress replyTo : replyToArray) {
            try {
                set.add(new MailAddress(replyTo));
            } catch (ParseException pe) {
                log("Unable to parse a \"REPLY_TO\" header address in the original message: " + replyTo + "; ignoring.");
            }
        }
    }

    /**
     * Returns a new Collection built over <i>list</i> replacing special
     * addresses with real <code>InternetAddress</code>-es.<br>
     * Manages <code>SpecialAddress.SENDER</code>,
     * <code>SpecialAddress.REVERSE_PATH</code>,
     * <code>SpecialAddress.FROM</code>, <code>SpecialAddress.REPLY_TO</code>,
     * <code>SpecialAddress.RECIPIENTS</code>, <code>SpecialAddress.TO</code>,
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code>.<br>
     * <code>SpecialAddress.RECIPIENTS</code> is made equivalent to
     * <code>SpecialAddress.TO</code>.<br>
     * <code>SpecialAddress.FROM</code> uses the From header if available,
     * otherwise the Sender header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.REPLY_TO</code> uses the ReplyTo header if
     * available, otherwise the From header if available, otherwise the Sender
     * header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.UNALTERED</code> is ignored.<br>
     * Any other address is not replaced.<br>
     */
    protected Collection<InternetAddress> replaceInternetAddresses(Mail mail, Collection<InternetAddress> list) throws MessagingException {
        ImmutableSet.Builder<InternetAddress> builder = ImmutableSet.builder();
        for (InternetAddress internetAddress : list) {
            MailAddress mailAddress = new MailAddress(internetAddress);
            if (!isSpecialAddress(mailAddress)) {
                builder.add(internetAddress);
                continue;
            }

            SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
            if (specialAddressKind == null) {
                builder.add(mailAddress.toInternetAddress());
                continue;
            }

            switch (specialAddressKind) {
            case SENDER:
                MailAddress sender = mail.getSender();
                if (sender != null) {
                    builder.add(sender.toInternetAddress());
                }
                break;
            case REVERSE_PATH:
                MailAddress reversePath = mail.getSender();
                if (reversePath != null) {
                    builder.add(reversePath.toInternetAddress());
                }
                break;
            case FROM:
                try {
                    InternetAddress[] fromArray = (InternetAddress[]) mail.getMessage().getFrom();
                    builder.addAll(allOrSender(mail, fromArray));
                } catch (MessagingException me) {
                    log("Unable to parse the \"FROM\" header in the original message; ignoring.");
                }
                break;
            case REPLY_TO:
                try {
                    InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
                    builder.addAll(allOrSender(mail, replyToArray));
                } catch (MessagingException me) {
                    log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
                }
                break;
            case TO:
            case RECIPIENTS:
                builder.addAll(toHeaders(mail));
                break;
            case NULL:
            case UNALTERED:
                break;
            case DELETE:
                builder.add(internetAddress);
                break;
            }
        }
        return builder.build();
    }

    private ImmutableSet<InternetAddress> allOrSender(Mail mail, InternetAddress[] addresses) {
        if (addresses != null) {
            return ImmutableSet.copyOf(addresses);
        } else {
            MailAddress reversePath = mail.getSender();
            if (reversePath != null) {
                return ImmutableSet.of(reversePath.toInternetAddress());
            }
        }
        return ImmutableSet.of();
    }

    private ImmutableSet<InternetAddress> toHeaders(Mail mail) {
        try {
            String[] toHeaders = mail.getMessage().getHeader(RFC2822Headers.TO);
            if (toHeaders != null) {
                for (String toHeader : toHeaders) {
                    try {
                        InternetAddress[] originalToInternetAddresses = InternetAddress.parse(toHeader, false);
                        return ImmutableSet.copyOf(originalToInternetAddresses);
                    } catch (MessagingException ae) {
                        log("Unable to parse a \"TO\" header address in the original message: " + toHeader + "; ignoring.");
                    }
                }
            }
            return ImmutableSet.of();
        } catch (MessagingException ae) {
            log("Unable to parse the \"TO\" header  in the original message; ignoring.");
            return ImmutableSet.of();
        }
    }
}
