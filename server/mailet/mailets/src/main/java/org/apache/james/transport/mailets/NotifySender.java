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

import java.util.Collection;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetConfig;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Sends a notification message to the sender of a message.
 * </p>
 * <p>
 * A sender of the notification message can optionally be specified. If one is
 * not specified, the postmaster's address will be used.<br>
 * The "To:" header of the notification message can be set to "unaltered"; if
 * missing will be set to the sender of the notified message.<br>
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
 * &lt;mailet match="All" class="NotifySender">
 *   &lt;sender&gt;<i>an address or postmaster or sender or unaltered, default=postmaster</i>&lt;/sender&gt;
 *   &lt;attachError&gt;<i>true or false, default=false</i>&lt;/attachError&gt;
 *   &lt;prefix&gt;<i>optional subject prefix prepended to the original message</i>&lt;/prefix&gt;
 *   &lt;inline&gt;<i>see {@link Resend}, default=none</i>&lt;/inline&gt;
 *   &lt;attachment&gt;<i>see {@link Resend}, default=message</i>&lt;/attachment&gt;
 *   &lt;passThrough&gt;<i>true or false, default=true</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<i>unaltered or sender or from(optional, defaults to sender)</i>&lt;/to&gt;
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
 *   &lt;passThrough&gt;true&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false</i>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<i>unaltered or sender or from&lt</i>;/to&gt;
 *   &lt;recipients&gt;<b>sender</b>&lt;/recipients&gt;
 *   &lt;inline&gt;none&lt;/inline&gt;
 *   &lt;attachment&gt;message&lt;/attachment&gt;
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
public class NotifySender extends AbstractRedirect {

    private static final String[] CONFIGURABLE_PARAMETERS = new String[]{
            "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "attachStackTrace", "to" };
    private static final Set<MailAddress> RECIPIENT_MAIL_ADDRESSES = ImmutableSet.of(SpecialAddress.SENDER);

    private boolean attachStackTrace;
    private Optional<String> to = Optional.absent();

    @Override
    public void init(MailetConfig mailetConfig) throws MessagingException {
        super.init(mailetConfig);
        attachStackTrace = getInitParameter("attachStackTrace", false);
        to = Optional.fromNullable(getInitParameter("to"));
    }

    @Override
    public String getMailetInfo() {
        return "NotifySender Mailet";
    }

    @Override
    protected String[] getAllowedInitParameters() {
        return CONFIGURABLE_PARAMETERS;
    }

    @Override
    protected boolean isNotifyMailet() {
        return true;
    }

    @Override
    protected Collection<MailAddress> getRecipients() {
        return RECIPIENT_MAIL_ADDRESSES;
    }

    @Override
    protected InternetAddress[] getTo() throws MessagingException {
        if (to.isPresent()) {
            MailAddress specialAddress = getSpecialAddress(to.get(), new String[] { "sender", "unaltered", "from" });
            if (specialAddress != null) {
                return new InternetAddress[] { specialAddress.toInternetAddress() };
            }
            log("\"to\" parameter ignored, set to sender");
        }
        return new InternetAddress[] { SpecialAddress.SENDER.toInternetAddress() };
    }

    @Override
    protected boolean attachError() throws MessagingException {
        return attachStackTrace || super.attachError();
    }
}
