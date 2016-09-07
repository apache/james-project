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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.ParseException;

import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageUtil;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Throwables;

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

    protected abstract boolean isNotifyMailet();

    /**
     * Gets the expected init parameters.
     *
     * @return null meaning no check
     */
    protected String[] getAllowedInitParameters() {
        return null;
    }

    /**
     * Controls certain log messages.
     */
    protected boolean isDebug = false;

    /**
     * Holds the value of the <code>static</code> init parameter.
     */
    protected boolean isStatic = false;

    private static class AddressMarker {
        public static final MailAddress SENDER = mailAddressUncheckedException("sender", "address.marker");
        public static final MailAddress REVERSE_PATH = mailAddressUncheckedException("reverse.path", "address.marker");
        public static final MailAddress FROM = mailAddressUncheckedException("from", "address.marker");
        public static final MailAddress REPLY_TO = mailAddressUncheckedException("reply.to", "address.marker");
        public static final MailAddress TO = mailAddressUncheckedException("to", "address.marker");
        public static final MailAddress RECIPIENTS = mailAddressUncheckedException("recipients", "address.marker");
        public static final MailAddress DELETE = mailAddressUncheckedException("delete", "address.marker");
        public static final MailAddress UNALTERED = mailAddressUncheckedException("unaltered", "address.marker");
        public static final MailAddress NULL = mailAddressUncheckedException("null", "address.marker");

        private static MailAddress mailAddressUncheckedException(String localPart, String domain) {
            try {
                return new MailAddress(localPart, domain);
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
    protected static class SpecialAddress {
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

    protected static final int UNALTERED = 0;

    protected static final int HEADS = 1;

    protected static final int BODY = 2;

    protected static final int ALL = 3;

    protected static final int NONE = 4;

    protected static final int MESSAGE = 5;

    private boolean passThrough = false;
    private boolean fakeDomainCheck = true;
    private int attachmentType = NONE;
    private int inLineType = BODY;
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
            return Boolean.valueOf(getInitParameter("passThrough", "true"));
        }
        return Boolean.valueOf(getInitParameter("passThrough"));
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
        return Boolean.valueOf(getInitParameter("fakeDomainCheck"));
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
    protected int getInLineType() {
        if (isNotifyMailet()) {
            return getTypeCode(getInitParameter("inline", "none"));
        }
        return getTypeCode(getInitParameter("inline", "unaltered"));
    }

    /**
     * Gets the <code>inline</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getInLineType()}
     */
    protected int getInLineType(Mail originalMail) throws MessagingException {
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
    protected int getAttachmentType() {
        if (isNotifyMailet()) {
            return getTypeCode(getInitParameter("attachment", "message"));
        }
        return getTypeCode(getInitParameter("attachment", "none"));
    }

    /**
     * Gets the <code>attachment</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getAttachmentType()}
     */
    protected int getAttachmentType(Mail originalMail) throws MessagingException {
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
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);

        // First add the "local" notice
        // (either from conf or generic error message)
        out.println(getMessage());
        // And then the message from other mailets
        if (originalMail.getErrorMessage() != null) {
            out.println();
            out.println("Error message below:");
            out.println(originalMail.getErrorMessage());
        }
        out.println();
        out.println("Message details:");

        if (message.getSubject() != null) {
            out.println("  Subject: " + message.getSubject());
        }
        if (message.getSentDate() != null) {
            out.println("  Sent date: " + message.getSentDate());
        }
        out.println("  MAIL FROM: " + originalMail.getSender());
        Iterator<MailAddress> rcptTo = originalMail.getRecipients().iterator();
        out.println("  RCPT TO: " + rcptTo.next());
        while (rcptTo.hasNext()) {
            out.println("           " + rcptTo.next());
        }
        String[] addresses;
        addresses = message.getHeader(RFC2822Headers.FROM);
        if (addresses != null) {
            out.print("  From: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        addresses = message.getHeader(RFC2822Headers.TO);
        if (addresses != null) {
            out.print("  To: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        addresses = message.getHeader(RFC2822Headers.CC);
        if (addresses != null) {
            out.print("  CC: ");
            for (String address : addresses) {
                out.print(address + " ");
            }
            out.println();
        }
        out.println("  Size (in bytes): " + message.getSize());
        if (message.getLineCount() >= 0) {
            out.println("  Number of lines: " + message.getLineCount());
        }

        return sout.toString();
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
        Collection<MailAddress> newRecipients = new HashSet<MailAddress>();
        String addressList = getInitParameter("recipients");

        // if nothing was specified, return <code>null</code> meaning no change
        if (addressList == null) {
            return null;
        }

        try {
            InternetAddress[] iaarray = InternetAddress.parse(addressList, false);
            for (InternetAddress anIaarray : iaarray) {
                String addressString = anIaarray.getAddress();
                MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null"});
                if (specialAddress != null) {
                    newRecipients.add(specialAddress);
                } else {
                    newRecipients.add(new MailAddress(anIaarray));
                }
            }
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getRecipients() parsing: " + addressList, e);
        }
        if (newRecipients.size() == 0) {
            throw new MessagingException("Failed to initialize \"recipients\" list; empty <recipients> init parameter found.");
        }

        return newRecipients;
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
            if (recipients.size() == 1 && (recipients.contains(SpecialAddress.UNALTERED) || recipients.contains(SpecialAddress.RECIPIENTS))) {
                recipients = null;
            } else {
                recipients = replaceMailAddresses(originalMail, recipients);
            }
        }
        return recipients;
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

        InternetAddress[] iaarray;
        String addressList = getInitParameter("to");

        // if nothing was specified, return null meaning no change
        if (addressList == null) {
            return null;
        }

        try {
            iaarray = InternetAddress.parse(addressList, false);
            for (int i = 0; i < iaarray.length; ++i) {
                String addressString = iaarray[i].getAddress();
                MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null"});
                if (specialAddress != null) {
                    iaarray[i] = specialAddress.toInternetAddress();
                }
            }
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getTo() parsing: " + addressList, e);
        }
        if (iaarray.length == 0) {
            throw new MessagingException("Failed to initialize \"to\" list; empty <to> init parameter found.");
        }

        return iaarray;
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
            if (apparentlyTo.length == 1 && (apparentlyTo[0].equals(SpecialAddress.UNALTERED.toInternetAddress()) || apparentlyTo[0].equals(SpecialAddress.TO.toInternetAddress()))) {
                apparentlyTo = null;
            } else {
                Collection<InternetAddress> toList = new ArrayList<InternetAddress>(apparentlyTo.length);
                Collections.addAll(toList, apparentlyTo);
                /*
                 * IMPORTANT: setTo() treats null differently from a zero length
                 * array, so it's ok to get a zero length array from
                 * replaceSpecialAddresses
                 */
                Collection<InternetAddress> var = replaceInternetAddresses(originalMail, toList);
                apparentlyTo = var.toArray(new InternetAddress[var.size()]);
            }
        }

        return apparentlyTo;
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

        String addressString = getInitParameter("replyTo", getInitParameter("replyto"));

        if (addressString != null) {
            MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "null", "unaltered"});
            if (specialAddress != null) {
                return specialAddress;
            }

            try {
                return new MailAddress(addressString);
            } catch (Exception e) {
                throw new MessagingException("Exception thrown in getReplyTo() parsing: " + addressString, e);
            }
        }

        return null;
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
            if (replyTo == SpecialAddress.UNALTERED) {
                replyTo = null;
            } else if (replyTo == SpecialAddress.SENDER) {
                replyTo = originalMail.getSender();
            }
        }
        return replyTo;
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
            InternetAddress[] iart = null;
            if (replyTo != SpecialAddress.NULL) {
                iart = new InternetAddress[1];
                iart[0] = replyTo.toInternetAddress();
            }

            // Note: if iart is null will remove the header
            newMail.getMessage().setReplyTo(iart);

            if (isDebug) {
                log("replyTo set to: " + replyTo);
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
        String addressString = getInitParameter("reversePath");
        if (addressString != null) {
            MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "null", "unaltered"});
            if (specialAddress != null) {
                return specialAddress;
            }

            try {
                return new MailAddress(addressString);
            } catch (Exception e) {
                throw new MessagingException("Exception thrown in getReversePath() parsing: " + addressString, e);
            }
        }

        return null;
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
            if (reversePath == SpecialAddress.UNALTERED || reversePath == SpecialAddress.REVERSE_PATH) {
                reversePath = null;
            } else if (reversePath == SpecialAddress.SENDER) {
                reversePath = null;
            }
        }
        return reversePath;
    }

    /**
     * Sets the "reverse-path" of <i>newMail</i> to <i>reversePath</i>. If the
     * requested value is <code>SpecialAddress.NULL</code> sets it to "<>". If
     * the requested value is null does nothing. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    protected void setReversePath(MailImpl newMail, MailAddress reversePath, Mail originalMail) {
        if (reversePath != null) {
            if (reversePath == SpecialAddress.NULL) {
                reversePath = null;
            }
            newMail.setSender(reversePath);
            if (isDebug) {
                log("reversePath set to: " + reversePath);
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
        String addressString = getInitParameter("sender");
        if (addressString != null) {
            MailAddress specialAddress = getSpecialAddress(addressString, new String[]{"postmaster", "sender", "unaltered"});
            if (specialAddress != null) {
                return specialAddress;
            }

            try {
                return new MailAddress(addressString);
            } catch (Exception e) {
                throw new MessagingException("Exception thrown in getSender() parsing: " + addressString, e);
            }
        }

        return null;
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
            if (sender == SpecialAddress.UNALTERED || sender == SpecialAddress.SENDER) {
                sender = null;
            }
        }
        return sender;
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
            String subject = originalMail.getMessage().getSubject();
            if (subject == null) {
                subject = "";
            }
            if (subjectPrefix == null || subject.indexOf(subjectPrefix) == 0) {
                newMail.getMessage().setSubject(subject);
            } else {
                newMail.getMessage().setSubject(subjectPrefix + subject);
            }
        }

        String subject = getSubject(originalMail);
        if ((subjectPrefix != null && subjectPrefix.length() > 0) || subject != null) {
            if (subject == null) {
                subject = originalMail.getMessage().getSubject();
            } else {
                // replacing the subject
                if (isDebug) {
                    log("subject set to: " + subject);
                }
            }
            // Was null in original?
            if (subject == null) {
                subject = "";
            }

            if (subjectPrefix != null) {
                subject = subjectPrefix + subject;
                // adding a prefix
                if (isDebug) {
                    log("subjectPrefix set to: " + subjectPrefix);
                }
            }
            // newMail.getMessage().setSubject(subject);
            changeSubject(newMail.getMessage(), subject);
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
        return Boolean.valueOf(getInitParameter("attachError"));
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
        return Boolean.valueOf(getInitParameter("isReply"));
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
    public void init() throws MessagingException {
        isDebug = Boolean.valueOf(getInitParameter("debug", "false"));

        isStatic = Boolean.valueOf(getInitParameter("static", "false"));

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
    public void service(Mail originalMail) throws MessagingException {

        boolean keepMessageId = false;

        // duplicates the Mail object, to be able to modify the new mail keeping
        // the original untouched
        MailImpl newMail = new MailImpl(originalMail);
        try {
            // We don't need to use the original Remote Address and Host,
            // and doing so would likely cause a loop with spam detecting
            // matchers.
            try {
                newMail.setRemoteAddr(dns.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                newMail.setRemoteAddr("127.0.0.1");

            }
            try {
                newMail.setRemoteHost(dns.getLocalHost().getHostName());
            } catch (UnknownHostException e) {
                newMail.setRemoteHost("localhost");
            }

            if (isDebug) {
                log("New mail - sender: " + newMail.getSender() + ", recipients: " + arrayToString(newMail.getRecipients().toArray()) + ", name: " + newMail.getName() + ", remoteHost: " + newMail.getRemoteHost() + ", remoteAddr: " + newMail.getRemoteAddr() + ", state: " + newMail.getState()
                        + ", lastUpdated: " + newMail.getLastUpdated() + ", errorMessage: " + newMail.getErrorMessage());
            }

            // Create the message
            if (getInLineType(originalMail) != UNALTERED) {
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

    /**
     * A private method to convert types from string to int.
     *
     * @param param the string type
     * @return the corresponding int enumeration
     */
    protected int getTypeCode(String param) {
        param = param.toLowerCase(Locale.US);
        if (param.compareTo("unaltered") == 0) {
            return UNALTERED;
        }
        if (param.compareTo("heads") == 0) {
            return HEADS;
        }
        if (param.compareTo("body") == 0) {
            return BODY;
        }
        if (param.compareTo("all") == 0) {
            return ALL;
        }
        if (param.compareTo("none") == 0) {
            return NONE;
        }
        if (param.compareTo("message") == 0) {
            return MESSAGE;
        }
        return NONE;
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
        java.io.ByteArrayOutputStream bodyOs = new java.io.ByteArrayOutputStream();
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
        String[] relevantHeaderNames = {RFC2822Headers.DATE, RFC2822Headers.FROM, RFC2822Headers.REPLY_TO, RFC2822Headers.TO, RFC2822Headers.SUBJECT, RFC2822Headers.RETURN_PATH};
        @SuppressWarnings("unchecked")
        Enumeration<String> headerEnum = originalMessage.getMatchingHeaderLines(relevantHeaderNames);
        while (headerEnum.hasMoreElements()) {
            newMessage.addHeaderLine((String) headerEnum.nextElement());
        }

        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        String head = getMessageHeaders(originalMessage);
        boolean all = false;

        String messageText = getMessage(originalMail);
        if (messageText != null) {
            out.println(messageText);
        }

        if (isDebug) {
            log("inline:" + getInLineType(originalMail));
        }
        switch (getInLineType(originalMail)) {
            case ALL: // ALL:
                all = true;
            case HEADS: // HEADS:
                out.println("Message Headers:");
                out.println(head);
                if (!all) {
                    break;
                }
            case BODY: // BODY:
                out.println("Message:");
                try {
                    out.println(getMessageBody(originalMessage));
                } catch (Exception e) {
                    out.println("body unavailable");
                }
                break;
            default:
            case NONE: // NONE:
                break;
        }

        try {
            // Create the message body
            MimeMultipart multipart = new MimeMultipart("mixed");

            // Create the message
            MimeMultipart mpContent = new MimeMultipart("alternative");
            MimeBodyPart contentPartRoot = new MimeBodyPart();
            contentPartRoot.setContent(mpContent);

            multipart.addBodyPart(contentPartRoot);

            MimeBodyPart part = new MimeBodyPart();
            part.setText(sout.toString());
            part.setDisposition("inline");
            mpContent.addBodyPart(part);
            if (isDebug) {
                log("attachmentType:" + getAttachmentType(originalMail));
            }
            if (getAttachmentType(originalMail) != NONE) {
                part = new MimeBodyPart();
                switch (getAttachmentType(originalMail)) {
                    case HEADS: // HEADS:
                        part.setText(head);
                        break;
                    case BODY: // BODY:
                        try {
                            part.setText(getMessageBody(originalMessage));
                        } catch (Exception e) {
                            part.setText("body unavailable");
                        }
                        break;
                    case ALL: // ALL:
                        String textBuffer = head + "\r\nMessage:\r\n" + getMessageBody(originalMessage);
                        part.setText(textBuffer);
                        break;
                    case MESSAGE: // MESSAGE:
                        part.setContent(originalMessage, "message/rfc822");
                        break;
                }
                if ((originalMessage.getSubject() != null) && (originalMessage.getSubject().trim().length() > 0)) {
                    part.setFileName(originalMessage.getSubject().trim());
                } else {
                    part.setFileName("No Subject");
                }
                part.setDisposition("Attachment");
                multipart.addBodyPart(part);
            }
            // if set, attach the original mail's error message
            if (attachError(originalMail) && originalMail.getErrorMessage() != null) {
                part = new MimeBodyPart();
                part.setContent(originalMail.getErrorMessage(), "text/plain");
                part.setHeader(RFC2822Headers.CONTENT_TYPE, "text/plain");
                part.setFileName("Reasons");
                part.setDisposition(javax.mail.Part.ATTACHMENT);
                multipart.addBodyPart(part);
            }
            newMail.getMessage().setContent(multipart);
            newMail.getMessage().setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());

        } catch (Exception ioe) {
            throw new MessagingException("Unable to create multipart body", ioe);
        }
    }

    /**
     * Sets the message id of originalMail into newMail.
     */
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

        addressString = addressString.toLowerCase(Locale.US);
        addressString = addressString.trim();

        MailAddress specialAddress = null;

        if (addressString.compareTo("postmaster") == 0) {
            specialAddress = getMailetContext().getPostmaster();
        }
        if (addressString.compareTo("sender") == 0) {
            specialAddress = SpecialAddress.SENDER;
        }
        if (addressString.compareTo("reversepath") == 0) {
            specialAddress = SpecialAddress.REVERSE_PATH;
        }
        if (addressString.compareTo("from") == 0) {
            specialAddress = SpecialAddress.FROM;
        }
        if (addressString.compareTo("replyto") == 0) {
            specialAddress = SpecialAddress.REPLY_TO;
        }
        if (addressString.compareTo("to") == 0) {
            specialAddress = SpecialAddress.TO;
        }
        if (addressString.compareTo("recipients") == 0) {
            specialAddress = SpecialAddress.RECIPIENTS;
        }
        if (addressString.compareTo("delete") == 0) {
            specialAddress = SpecialAddress.DELETE;
        }
        if (addressString.compareTo("unaltered") == 0) {
            specialAddress = SpecialAddress.UNALTERED;
        }
        if (addressString.compareTo("null") == 0) {
            specialAddress = SpecialAddress.NULL;
        }

        // if is a special address, must be in the allowedSpecials array
        if (specialAddress != null) {
            // check if is an allowed special
            boolean allowed = false;
            for (String allowedSpecial1 : allowedSpecials) {
                String allowedSpecial = allowedSpecial1;
                allowedSpecial = allowedSpecial.toLowerCase(Locale.US);
                allowedSpecial = allowedSpecial.trim();
                if (addressString.compareTo(allowedSpecial) == 0) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new MessagingException("Special (\"magic\") address found not allowed: " + addressString + ", allowed values are \"" + arrayToString(allowedSpecials) + "\"");
            }
        }

        return specialAddress;
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
        return !getFakeDomainCheck(mail) || mail.getSender() == null || getMailetContext().getMailServers(mail.getSender().getDomain()).size() != 0;
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
    public static void changeSubject(MimeMessage message, String newValue) throws MessagingException {
        String rawSubject = message.getHeader(RFC2822Headers.SUBJECT, null);
        String mimeCharset = determineMailHeaderEncodingCharset(rawSubject);
        if (mimeCharset == null) { // most likely ASCII
            // it uses the system charset or the value of the
            // mail.mime.charset property if set
            message.setSubject(newValue);
        } else { // original charset determined
            String javaCharset = javax.mail.internet.MimeUtility.javaCharset(mimeCharset);
            try {
                message.setSubject(newValue, javaCharset);
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
    static private String determineMailHeaderEncodingCharset(String rawText) {
        if (rawText == null)
            return null;
        int iEncodingPrefix = rawText.indexOf("=?");
        if (iEncodingPrefix == -1)
            return null;
        int iCharsetBegin = iEncodingPrefix + 2;
        int iSecondQuestionMark = rawText.indexOf('?', iCharsetBegin);
        if (iSecondQuestionMark == -1)
            return null;
        // safety checks
        if (iSecondQuestionMark == iCharsetBegin)
            return null; // empty charset? impossible
        int iThirdQuestionMark = rawText.indexOf('?', iSecondQuestionMark + 1);
        if (iThirdQuestionMark == -1)
            return null; // there must be one after encoding
        if (-1 == rawText.indexOf("?=", iThirdQuestionMark + 1))
            return null; // closing tag
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
        Collection<MailAddress> newList = new HashSet<MailAddress>(list.size());
        for (Object aList : list) {
            MailAddress mailAddress = (MailAddress) aList;
            if (!mailAddress.getDomain().equalsIgnoreCase("address.marker")) {
                newList.add(mailAddress);
            } else if (mailAddress == SpecialAddress.SENDER || mailAddress == SpecialAddress.FROM) {
                MailAddress sender = mail.getSender();
                if (sender != null) {
                    newList.add(sender);
                }
            } else if (mailAddress == SpecialAddress.REPLY_TO) {
                int parsedAddressCount = 0;
                try {
                    InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
                    if (replyToArray != null) {
                        for (InternetAddress aReplyToArray : replyToArray) {
                            try {
                                newList.add(new MailAddress(aReplyToArray));
                                parsedAddressCount++;
                            } catch (ParseException pe) {
                                log("Unable to parse a \"REPLY_TO\" header address in the original message: " + aReplyToArray + "; ignoring.");
                            }
                        }
                    }
                } catch (MessagingException ae) {
                    log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
                }
                // no address was parsed?
                if (parsedAddressCount == 0) {
                    MailAddress sender = mail.getSender();
                    if (sender != null) {
                        newList.add(sender);
                    }
                }
            } else if (mailAddress == SpecialAddress.REVERSE_PATH) {
                MailAddress reversePath = mail.getSender();
                if (reversePath != null) {
                    newList.add(reversePath);
                }
            } else if (mailAddress == SpecialAddress.RECIPIENTS || mailAddress == SpecialAddress.TO) {
                newList.addAll(mail.getRecipients());
            } else if (mailAddress == SpecialAddress.UNALTERED) {
            } else if (mailAddress == SpecialAddress.NULL) {
            } else {
                newList.add(mailAddress);
            }
        }
        return newList;
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
        Collection<InternetAddress> newList = new HashSet<InternetAddress>(list.size());
        for (InternetAddress internetAddress : list) {
            MailAddress mailAddress = new MailAddress(internetAddress);
            if (!mailAddress.getDomain().equalsIgnoreCase("address.marker")) {
                newList.add(internetAddress);
            } else if (internetAddress.equals(SpecialAddress.SENDER.toInternetAddress())) {
                MailAddress sender = mail.getSender();
                if (sender != null) {
                    newList.add(sender.toInternetAddress());
                }
            } else if (internetAddress.equals(SpecialAddress.REVERSE_PATH.toInternetAddress())) {
                MailAddress reversePath = mail.getSender();
                if (reversePath != null) {
                    newList.add(reversePath.toInternetAddress());
                }
            } else if (internetAddress.equals(SpecialAddress.FROM.toInternetAddress())) {
                try {
                    InternetAddress[] fromArray = (InternetAddress[]) mail.getMessage().getFrom();
                    if (fromArray != null) {
                        Collections.addAll(newList, fromArray);
                    } else {
                        MailAddress reversePath = mail.getSender();
                        if (reversePath != null) {
                            newList.add(reversePath.toInternetAddress());
                        }
                    }
                } catch (MessagingException me) {
                    log("Unable to parse the \"FROM\" header in the original message; ignoring.");
                }
            } else if (internetAddress.equals(SpecialAddress.REPLY_TO.toInternetAddress())) {
                try {
                    InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
                    if (replyToArray != null) {
                        Collections.addAll(newList, replyToArray);
                    } else {
                        MailAddress reversePath = mail.getSender();
                        if (reversePath != null) {
                            newList.add(reversePath.toInternetAddress());
                        }
                    }
                } catch (MessagingException me) {
                    log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
                }
            } else if (internetAddress.equals(SpecialAddress.TO.toInternetAddress()) || internetAddress.equals(SpecialAddress.RECIPIENTS.toInternetAddress())) {
                try {
                    String[] toHeaders = mail.getMessage().getHeader(RFC2822Headers.TO);
                    if (toHeaders != null) {
                        for (String toHeader : toHeaders) {
                            try {
                                InternetAddress[] originalToInternetAddresses = InternetAddress.parse(toHeader, false);
                                Collections.addAll(newList, originalToInternetAddresses);
                            } catch (MessagingException ae) {
                                log("Unable to parse a \"TO\" header address in the original message: " + toHeader + "; ignoring.");
                            }
                        }
                    }
                } catch (MessagingException ae) {
                    log("Unable to parse the \"TO\" header  in the original message; ignoring.");
                }
            } else if (internetAddress.equals(SpecialAddress.UNALTERED.toInternetAddress())) {
            } else if (internetAddress.equals(SpecialAddress.NULL.toInternetAddress())) {
            } else {
                newList.add(internetAddress);
            }
        }
        return newList;
    }

}
