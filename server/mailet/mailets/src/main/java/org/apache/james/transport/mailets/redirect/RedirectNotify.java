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
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.Redirect;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

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

public interface RedirectNotify extends Mailet, MailetConfig {

    List<String> REVERSE_PATH_ALLOWED_SPECIALS = ImmutableList.of("postmaster", "sender", "null", "unaltered");
    List<String> SENDER_ALLOWED_SPECIALS = ImmutableList.of("postmaster", "sender", "unaltered");

    InitParameters getInitParameters();

    Set<String> getAllowedInitParameters();

    DNSService getDNSService();

    @Deprecated
    void log(String message);
    
    @Deprecated
    void log(String message, Throwable t);

    /**
     * Gets the <code>message</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getMessage()}
     */
    String getMessage(Mail originalMail) throws MessagingException;

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
    List<MailAddress> getRecipients() throws MessagingException; 

    /**
     * Gets the <code>recipients</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #replaceMailAddresses} on {@link #getRecipients()},
     */
    List<MailAddress> getRecipients(Mail originalMail) throws MessagingException; 

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
    List<InternetAddress> getTo() throws MessagingException;

    /**
     * Gets the <code>to</code> property, built dynamically using the original
     * Mail object. Its outcome will be the the value the <i>TO:</i> header will
     * be set to, that could be different from the real recipient (see
     * {@link Mail#getRecipients}). Is a "getX(Mail)" method.
     *
     * @return {@link #replaceInternetAddresses} on {@link #getRecipients()},
     */
    List<MailAddress> getTo(Mail originalMail) throws MessagingException;

    /**
     * Gets the <code>replyto</code> property. Returns the Reply-To address of
     * the new message, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>replyto</code> init parameter or the postmaster address
     *         or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.UNALTERED</code> or
     *         <code>SpecialAddress.NULL</code> or <code>null</code> if missing
     */
    Optional<MailAddress> getReplyTo() throws MessagingException;

    /**
     * Gets the <code>replyTo</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return Optional of {@link #getReplyTo()} replacing
     *         <code>SpecialAddress.UNALTERED</code> if applicable with null and
     *         <code>SpecialAddress.SENDER</code> with the original mail sender
     */
    Optional<MailAddress> getReplyTo(Mail originalMail) throws MessagingException;

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
    Optional<MailAddress> getReversePath() throws MessagingException; 

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
    Optional<MailAddress> getReversePath(Mail originalMail) throws MessagingException;

    /**
     * Gets the <code>sender</code> property. Returns the new sender as a
     * MailAddress, or null if no change is requested. Is a "getX()" method.
     *
     * @return the <code>sender</code> init parameter or the postmaster address
     *         or <code>SpecialAddress.SENDER</code> or
     *         <code>SpecialAddress.UNALTERED</code> or <code>null</code> if
     *         missing
     */
    Optional<MailAddress> getSender() throws MessagingException; 

    /**
     * Gets the <code>sender</code> property, built dynamically using the
     * original Mail object. Is a "getX(Mail)" method.
     *
     * @return {@link #getSender()} replacing
     *         <code>SpecialAddress.UNALTERED</code> and
     *         <code>SpecialAddress.SENDER</code> if applicable with null
     */
    Optional<MailAddress> getSender(Mail originalMail) throws MessagingException;

    /**
     * Builds the subject of <i>newMail</i> appending the subject of
     * <i>originalMail</i> to <i>subjectPrefix</i>. Is a "setX(Mail, Tx, Mail)"
     * method.
     */
    Optional<String> getSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException;

    /**
     * Service does the hard work,and redirects the originalMail in the form
     * specified.
     *
     * @param originalMail the mail to process and redirect
     * @throws MessagingException if a problem arises formulating the redirected mail
     */
    @Override
    void service(Mail originalMail) throws MessagingException;
}
