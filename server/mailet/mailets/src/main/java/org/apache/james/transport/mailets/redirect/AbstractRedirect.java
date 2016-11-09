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

import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageUtil;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.util.MailAddressUtils;
import org.apache.james.transport.util.SpecialAddressesUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

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
    protected abstract String getMessage(Mail originalMail) throws MessagingException;

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
    protected List<MailAddress> getRecipients() throws MessagingException {
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
    protected List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        List<MailAddress> recipients = getRecipients();
        if (recipients != null) {
            if (containsOnlyUnalteredOrRecipients(recipients)) {
                return null;
            }
            return SpecialAddressesUtils.from(this).replaceSpecialAddresses(originalMail, recipients);
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
    protected abstract List<InternetAddress> getTo() throws MessagingException;

    /**
     * Gets the <code>to</code> property, built dynamically using the original
     * Mail object. Its outcome will be the the value the <i>TO:</i> header will
     * be set to, that could be different from the real recipient (see
     * {@link Mail#getRecipients}). Is a "getX(Mail)" method.
     *
     * @return {@link #replaceInternetAddresses} on {@link #getRecipients()},
     */
    protected List<MailAddress> getTo(Mail originalMail) throws MessagingException {
        List<InternetAddress> apparentlyTo = getTo();
        if (!apparentlyTo.isEmpty()) {
            if (containsOnlyUnalteredOrTo(apparentlyTo)) {
                return null;
            }
            return SpecialAddressesUtils.from(this).replaceInternetAddresses(originalMail, apparentlyTo);
        }

        return null;
    }

    private boolean containsOnlyUnalteredOrTo(List<InternetAddress> apparentlyTo) {
        return apparentlyTo.size() == 1 && 
                (apparentlyTo.get(0).equals(SpecialAddress.UNALTERED.toInternetAddress()) 
                        || apparentlyTo.get(0).equals(SpecialAddress.RECIPIENTS.toInternetAddress()));
    }

    /**
     * Sets the "To:" header of <i>newMail</i> to <i>to</i>. If the requested
     * value is null does nothing. Is a "setX(Mail, Tx, Mail)" method.
     */
    protected void setTo(Mail newMail, List<MailAddress> mailAddresses, Mail originalMail) throws MessagingException {
        if (mailAddresses != null) {
            InternetAddress[] internetAddresses = MailAddressUtils.toInternetAddressArray(mailAddresses);
            newMail.getMessage().setRecipients(Message.RecipientType.TO, internetAddresses);
            if (getInitParameters().isDebug()) {
                log("apparentlyTo set to: " + internetAddresses);
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
    protected abstract MailAddress getReplyTo() throws MessagingException;

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
    protected abstract MailAddress getReversePath(Mail originalMail) throws MessagingException;

    protected boolean isUnalteredOrReversePathOrSender(MailAddress reversePath) {
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
    protected abstract void setSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException;

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
}
