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

import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.HashSet;
import java.util.Collection;

/**
 * <p>
 * Sends a notification message to the Postmaster.
 * </p>
 * <p>
 * A sender of the notification message can optionally be specified. If one is
 * not specified, the postmaster's address will be used.<br>
 * The "To:" header of the notification message can be set to "unaltered"; if
 * missing will be set to the postmaster.<br>
 * A notice text can be specified, and in such case will be inserted into the
 * notification inline text.<br>
 * If the notified message has an "error message" set, it will be inserted into
 * the notification inline text. If the <code>attachStackTrace</code> init
 * parameter is set to true, such error message will be attached to the
 * notification message.<br>
 * The notified messages are attached in their entirety (headers and content)
 * and the resulting MIME part type is "message/rfc822".
 * </p>
 * <p>
 * Supports the <code>passThrough</code> init parameter (true if missing).
 * </p>
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="NotifyPostmaster">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered, default=postmaster</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false, default=false</i>&lt;/attachError&gt;
 *   &lt;message&gt;<i>notice attached to the original message text (optional)</i>&lt;/message&gt;
 *   &lt;prefix&gt;<i>optional subject prefix prepended to the original message, default="Re:"</i>&lt;/prefix&gt;
 *   &lt;inline&gt;<i>see {@link Resend}, default=none</i>&lt;/inline&gt;
 *   &lt;attachment&gt;<i>see {@link Resend}, default=message</i>&lt;/attachment&gt;
 *   &lt;passThrough&gt;<i>true or false, default=true</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<i>unaltered (optional, defaults to postmaster)</i>&lt;/to&gt;
 *   &lt;debug&gt;<i>true or false, default=false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * <p>
 * The behaviour of this mailet is equivalent to using Resend with the following
 * configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="Resend">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false</i>&lt;/attachError&gt;
 *   &lt;message&gt;<i><b>dynamically built</b></i>&lt;/message&gt;
 *   &lt;prefix&gt;<i>a string</i>&lt;/prefix&gt;
 *   &lt;passThrough&gt;<i>true or false</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false</i>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<i><b>unaltered or postmaster</b></i>&lt;/to&gt;
 *   &lt;recipients&gt;<b>postmaster</b>&lt;/recipients&gt;
 *   &lt;inline&gt;see {@link Resend}&lt;/inline&gt;
 *   &lt;attachment&gt;see {@link Resend}&lt;/attachment&gt;
 *   &lt;isReply&gt;true&lt;/isReply&gt;
 *   &lt;debug&gt;<i>true or false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>notice</i>, <i>sendingAddress</i> and <i>attachStackTrace</i> can be used
 * instead of <i>message</i>, <i>sender</i> and <i>attachError</i>; such names
 * are kept for backward compatibility.
 * </p>
 */
public class NotifyPostmaster extends AbstractNotify {

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "NotifyPostmaster Mailet";
    }

    /** Gets the expected init parameters. */
    protected String[] getAllowedInitParameters() {
        String[] allowedArray = {
                // "static",
                "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "attachStackTrace", "to" };
        return allowedArray;
    }

    /**
     * @return the postmaster address
     */
    protected Collection<MailAddress> getRecipients() {
        Collection<MailAddress> newRecipients = new HashSet<MailAddress>();
        newRecipients.add(getMailetContext().getPostmaster());
        return newRecipients;
    }

    /**
     * @return <code>SpecialAddress.UNALTERED</code> if specified or postmaster
     *         if missing
     */
    protected InternetAddress[] getTo() throws MessagingException {
        String addressList = getInitParameter("to");
        InternetAddress[] iaarray = new InternetAddress[1];
        iaarray[0] = getMailetContext().getPostmaster().toInternetAddress();
        if (addressList != null) {
            MailAddress specialAddress = getSpecialAddress(addressList, new String[] { "postmaster", "unaltered" });
            if (specialAddress != null) {
                iaarray[0] = specialAddress.toInternetAddress();
            } else {
                log("\"to\" parameter ignored, set to postmaster");
            }
        }
        return iaarray;
    }

    /**
     * @return the <code>attachStackTrace</code> init parameter, or the
     *         <code>attachError</code> init parameter if missing, or false if
     *         missing
     */
    protected boolean attachError() throws MessagingException {
        String parameter = getInitParameter("attachStackTrace");
        if (parameter == null) {
            return super.attachError();
        }
        return Boolean.valueOf(parameter);
    }

}
