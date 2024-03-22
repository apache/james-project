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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.NotifyMailetInitParameters;
import org.apache.james.transport.mailets.redirect.NotifyMailetsMessage;
import org.apache.james.transport.mailets.redirect.ProcessRedirectNotify;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.james.transport.util.RecipientsUtils;
import org.apache.james.transport.util.ReplyToUtils;
import org.apache.james.transport.util.SenderUtils;
import org.apache.james.transport.util.SpecialAddressesUtils;
import org.apache.james.transport.util.TosUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
 * the notification inline text. If the <code>attachError</code> init
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
public class Bounce extends GenericMailet implements RedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bounce.class);

    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of(
            "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError");
    private static final List<MailAddress> RECIPIENTS = ImmutableList.of(SpecialAddress.REVERSE_PATH);
    private static final List<InternetAddress> TO = SpecialAddress.REVERSE_PATH.toInternetAddress().stream().collect(ImmutableList.toImmutableList());
    private final DNSService dns;

    @Inject
    public Bounce(DNSService dns) {
        this.dns = dns;
    }

    @Override
    public String getMailetInfo() {
        return "Bounce Mailet";
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
    }

    @Override
    public String getMessage(Mail originalMail) throws MessagingException {
        return new NotifyMailetsMessage().generateMessage(getInitParameters().getMessage(), originalMail);
    }

    @Override
    public List<MailAddress> getRecipients() {
        return RECIPIENTS;
    }

    @Override
    public List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        return RecipientsUtils.from(this).getRecipients(originalMail);
    }

    @Override
    public List<InternetAddress> getTo() {
        return TO;
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
    public Optional<MailAddress> getReversePath(Mail originalMail) {
        return Optional.of(SpecialAddress.NULL);
    }

    @Override
    public Optional<MailAddress> getReversePath() throws MessagingException {
        return SpecialAddressesUtils.from(this)
                .getFirstSpecialAddressIfMatchingOrGivenAddress(getInitParameters().getReversePath(), RedirectNotify.REVERSE_PATH_ALLOWED_SPECIALS);
    }

    @Override
    public Optional<MailAddress> getSender() throws MessagingException {
        return SpecialAddressesUtils.from(this)
                .getFirstSpecialAddressIfMatchingOrGivenAddress(getInitParameters().getSender(), RedirectNotify.SENDER_ALLOWED_SPECIALS);
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
        if (!originalMail.hasSender()) {
            passThrough(originalMail);
        } else {
            if (getInitParameters().isDebug()) {
                LOGGER.debug("Processing a bounce request for a message with a reverse path.  The bounce will be sent to {}", originalMail.getMaybeSender());
            }
            ProcessRedirectNotify.from(this).process(originalMail);
        }
    }

    private void passThrough(Mail originalMail) {
        if (getInitParameters().isDebug()) {
            LOGGER.debug("Processing a bounce request for a message with an empty reverse-path.  No bounce will be sent.");
        }
        if (!getInitParameters().getPassThrough()) {
            originalMail.setState(Mail.GHOST);
        }
    }
}
