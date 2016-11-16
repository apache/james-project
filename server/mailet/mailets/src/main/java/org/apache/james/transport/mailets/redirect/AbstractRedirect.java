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

import java.util.List;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailImpl;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Optional;
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

    public static final List<String> REVERSE_PATH_ALLOWED_SPECIALS = ImmutableList.of("postmaster", "sender", "null", "unaltered");
    public static final List<String> SENDER_ALLOWED_SPECIALS = ImmutableList.of("postmaster", "sender", "unaltered");

    public abstract InitParameters getInitParameters();

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
    public abstract String getMessage(Mail originalMail) throws MessagingException;

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
    public abstract List<MailAddress> getRecipients() throws MessagingException; 

    /**
     * Gets the <code>recipients</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #replaceMailAddresses} on {@link #getRecipients()},
     */
    protected abstract List<MailAddress> getRecipients(Mail originalMail) throws MessagingException; 

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
    public abstract List<InternetAddress> getTo() throws MessagingException;

    /**
     * Gets the <code>to</code> property, built dynamically using the original
     * Mail object. Its outcome will be the the value the <i>TO:</i> header will
     * be set to, that could be different from the real recipient (see
     * {@link Mail#getRecipients}). Is a "getX(Mail)" method.
     *
     * @return {@link #replaceInternetAddresses} on {@link #getRecipients()},
     */
    protected abstract List<MailAddress> getTo(Mail originalMail) throws MessagingException;

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
     * Gets the <code>reversePath</code> property. Returns the reverse-path of
     * the new message, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>reversePath</code> init parameter or the postmaster
     *         address or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.NULL</code> or
     *         <code>SpecialAddress.UNALTERED</code> or <code>null</code> if
     *         missing
     */
    protected abstract MailAddress getReversePath() throws MessagingException; 

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

    /**
     * Gets the <code>sender</code> property. Returns the new sender as a
     * MailAddress, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>sender</code> init parameter or the postmaster address
     *         or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.UNALTERED</code> or <code>null</code> if
     *         missing
     */
    protected abstract MailAddress getSender() throws MessagingException; 

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
     * Builds the subject of <i>newMail</i> appending the subject of
     * <i>originalMail</i> to <i>subjectPrefix</i>. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    protected abstract Optional<String> getSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException;

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
            MailModifier mailModifier = MailModifier.builder()
                    .mailet(this)
                    .mail(newMail)
                    .dns(dns)
                    .build();
            mailModifier.setRemoteAddr();
            mailModifier.setRemoteHost();

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
                AlteredMailUtils.builder()
                    .mailet(this)
                    .originalMail(originalMail)
                    .build()
                    .buildAlteredMessage(newMail);

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

            mailModifier.setRecipients(getRecipients(originalMail));
            mailModifier.setTo(getTo(originalMail));
            mailModifier.setSubjectPrefix(originalMail);
            mailModifier.setReplyTo(getReplyTo(originalMail), originalMail);
            mailModifier.setReversePath(getReversePath(originalMail), originalMail);
            mailModifier.setIsReply(getInitParameters().isReply(), originalMail);
            mailModifier.setSender(getSender(originalMail), originalMail);
            mailModifier.initializeDateIfNotPresent();
            if (keepMessageId) {
                mailModifier.setMessageId(originalMail);
            }
            newMail =  mailModifier.getMail();

            newMail.getMessage().saveChanges();
            newMail.removeAllAttributes();

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

    protected abstract MimeMessageModifier getMimeMessageModifier(Mail newMail, Mail originalMail) throws MessagingException;

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
