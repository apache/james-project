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
import java.util.List;

import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
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
import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
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
    protected abstract InitParameters getInitParameters();

    protected abstract String[] getAllowedInitParameters();

    protected DNSService dns;

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    /**
     * Gets the <code>message</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getMessage()}
     */
    protected String getMessage(Mail originalMail) throws MessagingException {
        if (isNotifyMailet()) {
            return new NotifyMailetsMessage().generateMessage(getInitParameters().getMessage(), originalMail);
        }
        return getInitParameters().getMessage();
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
        List<MailAddress> mailAddresses = AddressExtractor.withContext(getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null"))
                .extract(getInitParameters().getRecipients());
        for (MailAddress address : mailAddresses) {
            builder.add(address);
        }
        return builder.build();
    }

    /**
     * Gets the <code>recipients</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #replaceMailAddresses} on {@link #getRecipients()},
     */
    protected Collection<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        Collection<MailAddress> recipients = getRecipients();
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
            if (getInitParameters().isDebug()) {
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
        List<MailAddress> mailAddresses = AddressExtractor.withContext(getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null"))
                .extract(getInitParameters().getTo());
        for (MailAddress address : mailAddresses) {
            builder.add(address.toInternetAddress());
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
        InternetAddress[] apparentlyTo = getTo();
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
            if (getInitParameters().isDebug()) {
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

        String replyTo = getInitParameters().getReplyTo();
        if (Strings.isNullOrEmpty(replyTo)) {
            return null;
        }

        List<MailAddress> extractAddresses = AddressExtractor.withContext(getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "null", "unaltered"))
                .extract(replyTo);
        if (extractAddresses.isEmpty()) {
            return null;
        }
        return extractAddresses.get(0);
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
        MailAddress replyTo = getReplyTo();
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
                if (getInitParameters().isDebug()) {
                    log("replyTo set to: null");
                }
            } else {
                newMail.getMessage().setReplyTo(new InternetAddress[] { replyTo.toInternetAddress() });
                if (getInitParameters().isDebug()) {
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
        String reversePath = getInitParameters().getReversePath();
        if (Strings.isNullOrEmpty(reversePath)) {
            return null;
        }

        List<MailAddress> extractAddresses = AddressExtractor.withContext(getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "null", "unaltered"))
                .extract(reversePath);
        if (extractAddresses.isEmpty()) {
            return null;
        }
        return extractAddresses.get(0);
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

        MailAddress reversePath = getReversePath();
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
                if (getInitParameters().isDebug()) {
                    log("reversePath set to: null");
                }
            } else {
                newMail.setSender(reversePath);
                if (getInitParameters().isDebug()) {
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
        String sender = getInitParameters().getSender();
        if (Strings.isNullOrEmpty(sender)) {
            return null;
        }

        List<MailAddress> extractAddresses = AddressExtractor.withContext(getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "unaltered"))
                .extract(sender);
        if (extractAddresses.isEmpty()) {
            return null;
        }
        return extractAddresses.get(0);
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
        MailAddress sender = getSender();
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

            if (getInitParameters().isDebug()) {
                log("sender set to: " + sender);
            }
        }
    }

    /**
     * Builds the subject of <i>newMail</i> appending the subject of
     * <i>originalMail</i> to <i>subjectPrefix</i>. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    protected void setSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        if (isNotifyMailet()) {
            new MimeMessageModifier(originalMail.getMessage()).addSubjectPrefix(subjectPrefix);
        }

        String subject = getInitParameters().getSubject();
        if (!Strings.isNullOrEmpty(subjectPrefix) || subject != null) {
            String newSubject = Strings.nullToEmpty(subject);
            if (subject == null) {
                newSubject = Strings.nullToEmpty(originalMail.getMessage().getSubject());
            } else {
                if (getInitParameters().isDebug()) {
                    log("subject set to: " + subject);
                }
            }

            if (subjectPrefix != null) {
                newSubject = subjectPrefix + newSubject;
                if (getInitParameters().isDebug()) {
                    log("subjectPrefix set to: " + subjectPrefix);
                }
            }
            changeSubject(newMail.getMessage(), newSubject);
        }
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
                if (getInitParameters().isDebug()) {
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
        if (getInitParameters().isDebug()) {
            log("Initializing");
        }

        // check that all init parameters have been declared in
        // allowedInitParameters
        checkInitParameters(getAllowedInitParameters());

        if (getInitParameters().isStatic()) {
            if (getInitParameters().isDebug()) {
                log(getInitParameters().asString());
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

            if (getInitParameters().isDebug()) {
                log("New mail - sender: " + newMail.getSender() + ", recipients: " + arrayToString(newMail.getRecipients().toArray()) + ", name: " + newMail.getName() + ", remoteHost: " + newMail.getRemoteHost() + ", remoteAddr: " + newMail.getRemoteAddr() + ", state: " + newMail.getState()
                        + ", lastUpdated: " + newMail.getLastUpdated() + ", errorMessage: " + newMail.getErrorMessage());
            }

            // Create the message
            if (!getInitParameters().getInLineType().equals(TypeCode.UNALTERED)) {
                if (getInitParameters().isDebug()) {
                    log("Alter message");
                }
                newMail.setMessage(new MimeMessage(Session.getDefaultInstance(System.getProperties(), null)));

                // handle the new message if altered
                buildAlteredMessage(newMail, originalMail);

            } else {
                // if we need the original, create a copy of this message to
                // redirect
                if (getInitParameters().getPassThrough()) {
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
                if (getInitParameters().isDebug()) {
                    log("Message resent unaltered.");
                }
                keepMessageId = true;
            }

            // Set additional headers

            setRecipients(newMail, getRecipients(originalMail), originalMail);

            setTo(newMail, getTo(originalMail), originalMail);

            setSubjectPrefix(newMail, getInitParameters().getSubjectPrefix(), originalMail);

            if (newMail.getMessage().getHeader(RFC2822Headers.DATE) == null) {
                newMail.getMessage().setHeader(RFC2822Headers.DATE, DateFormats.RFC822_DATE_FORMAT.format(new Date()));
            }

            setReplyTo(newMail, getReplyTo(originalMail), originalMail);

            setReversePath(newMail, getReversePath(originalMail), originalMail);

            setSender(newMail, getSender(originalMail), originalMail);

            setIsReply(newMail, getInitParameters().isReply(), originalMail);

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

        if (!getInitParameters().getPassThrough()) {
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

            if (getInitParameters().isDebug()) {
                log("attachmentType:" + getInitParameters().getAttachmentType());
            }
            if (!getInitParameters().getAttachmentType().equals(TypeCode.NONE)) {
                multipart.addBodyPart(getAttachmentPart(originalMail, originalMessage, head));
            }

            if (getInitParameters().isAttachError() && originalMail.getErrorMessage() != null) {
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
        switch (getInitParameters().getAttachmentType()) {
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

        if (getInitParameters().isDebug()) {
            log("inline:" + getInitParameters().getInLineType());
        }
        boolean all = false;
        switch (getInitParameters().getInLineType()) {
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
            if (getInitParameters().isDebug()) {
                log("MESSAGE_ID restored to: " + messageId);
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
    protected final boolean senderDomainIsValid(Mail mail) throws MessagingException {
        return !getInitParameters().getFakeDomainCheck()
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
        Optional<String> mimeCharset = new CharsetFromSubjectMailHeader().parse(rawSubject);
        if (!mimeCharset.isPresent()) { // most likely ASCII
            // it uses the system charset or the value of the
            // mail.mime.charset property if set
            message.setSubject(newValue);
        } else { // original charset determined
            try {
                message.setSubject(newValue, MimeUtility.javaCharset(mimeCharset.get()));
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
            if (!SpecialAddress.isSpecialAddress(mailAddress)) {
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
            if (!SpecialAddress.isSpecialAddress(mailAddress)) {
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
