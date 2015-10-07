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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Collection;
import java.util.HashSet;

/**
 * <p>
 * Generates a response to the reverse-path address. Note that this is different
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
 * A notice text can be specified, and in such case will be inserted into the
 * notification inline text.<br>
 * If the notified message has an "error message" set, it will be inserted into
 * the notification inline text. If the <code>attachStackTrace</code> init
 * parameter is set to true, such error message will be attached to the
 * notification message.<br>
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
 * &lt;mailet match="All" class="Bounce">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered, default=postmaster</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false, default=false</i>&lt;/attachError&gt;
 *   &lt;message&gt;<i>notice attached to the original message text (optional)</i>&lt;/message&gt;
 *   &lt;prefix&gt;<i>optional subject prefix prepended to the original message</i>&lt;/prefix&gt;
 *   &lt;inline&gt;<i>see {@link Resend}, default=none</i>&lt;/inline&gt;
 *   &lt;attachment&gt;<i>see {@link Resend}, default=message</i>&lt;/attachment&gt;
 *   &lt;passThrough&gt;<i>true or false, default=true</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
 *   &lt;debug&gt;<i>true or false, default=false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p/>
 * <p>
 * The behaviour of this mailet is equivalent to using Resend with the following
 * configuration:
 * </p>
 * <p/>
 * <pre>
 * <code>
 * &lt;mailet match="All" class="Resend">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false</i>&lt;/attachError&gt;
 *   &lt;message&gt;<i><b>dynamically built</b></i>&lt;/message&gt;
 *   &lt;prefix&gt;<i>a string</i>&lt;/prefix&gt;
 *   &lt;passThrough&gt;true or false&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false</i>&lt;/fakeDomainCheck&gt;
 *   &lt;recipients&gt;<b>sender</b>&lt;/recipients&gt;
 *   &lt;reversePath&gt;null&lt;/reversePath&gt;
 *   &lt;inline&gt;see {@link Resend}&lt;/inline&gt;
 *   &lt;attachment&gt;see {@link Resend}&lt;/attachment&gt;
 *   &lt;isReply&gt;true&lt;/isReply&gt;
 *   &lt;debug&gt;<i>true or false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>notice</i> and <i>sendingAddress</i> can be used instead of <i>message</i>
 * and <i>sender</i>; such names are kept for backward compatibility.
 * </p>
 *
 * @since 2.2.0
 */
public class Bounce extends AbstractNotify {

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    @Override
    public String getMailetInfo() {
        return "Bounce Mailet";
    }

    /**
     * Gets the expected init parameters.
     */
    @Override
    protected String[] getAllowedInitParameters() {
        return new String[]{
                // "static",
                "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError",};
    }

    /**
     * @return <code>SpecialAddress.REVERSE_PATH</code>
     */
    @Override
    protected Collection getRecipients() {
        Collection newRecipients = new HashSet();
        newRecipients.add(SpecialAddress.REVERSE_PATH);
        return newRecipients;
    }

    /**
     * @return <code>SpecialAddress.REVERSE_PATH</code>
     */
    @Override
    protected InternetAddress[] getTo() {
        InternetAddress[] apparentlyTo = new InternetAddress[1];
        apparentlyTo[0] = SpecialAddress.REVERSE_PATH.toInternetAddress();
        return apparentlyTo;
    }

    /**
     * @return <code>SpecialAddress.NULL</code> (the meaning of bounce)
     */
    @Override
    protected MailAddress getReversePath(Mail originalMail) {
        return SpecialAddress.NULL;
    }

    /**
     * Service does the hard work,and redirects the originalMail in the form
     * specified. Checks that the original return path is not empty, and then
     * calls super.service(originalMail), otherwise just returns.
     *
     * @param originalMail the mail to process and redirect
     * @throws MessagingException if a problem arises formulating the redirected mail
     */
    @Override
    public void service(Mail originalMail) throws MessagingException {
        if (originalMail.getSender() == null) {
            if (isDebug)
                log("Processing a bounce request for a message with an empty reverse-path.  No bounce will be sent.");
            if (!getPassThrough(originalMail)) {
                originalMail.setState(Mail.GHOST);
            }
            return;
        }

        if (isDebug)
            log("Processing a bounce request for a message with a reverse path.  The bounce will be sent to " + originalMail.getSender().toString());

        super.service(originalMail);
    }

}
