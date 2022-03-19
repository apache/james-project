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

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.redirect.AddressExtractor;
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
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
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
 * the notification inline text. If the <code>attachError</code> init
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
 * <i>notice</i>, <i>sendingAddress</i> and <i>attachError</i> can be used
 * instead of <i>message</i>, <i>sender</i> and <i>attachError</i>; such names
 * are kept for backward compatibility.
 * </p>
 */
public class NotifySender extends GenericMailet implements RedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotifySender.class);

    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of(
            "debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "to");
    private static final List<MailAddress> RECIPIENT_MAIL_ADDRESSES = ImmutableList.of(SpecialAddress.SENDER);
    private static final List<String> ALLOWED_SPECIALS = ImmutableList.of("sender", "unaltered", "from");

    private final DNSService dns;
    private Optional<String> to = Optional.empty();

    @Inject
    NotifySender(DNSService dns) {
        this.dns = dns;
    }

    @Override
    public void init(MailetConfig mailetConfig) throws MessagingException {
        super.init(mailetConfig);
        to = Optional.ofNullable(getInitParameter("to"));
    }

    @Override
    public String getMailetInfo() {
        return "NotifySender Mailet";
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

        if (getInitParameters().isStatic()
                && getInitParameters().isDebug()) {
            LOGGER.debug(getInitParameters().asString());
        }
    }

    @Override
    public String getMessage(Mail originalMail) throws MessagingException {
        return new NotifyMailetsMessage().generateMessage(getInitParameters().getMessage(), originalMail);
    }

    @Override
    public List<MailAddress> getRecipients() {
        return RECIPIENT_MAIL_ADDRESSES;
    }

    @Override
    public List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        return RecipientsUtils.from(this).getRecipients(originalMail);
    }

    @Override
    public List<InternetAddress> getTo() throws MessagingException {
        if (to.isPresent()) {
            Optional<MailAddress> specialAddress = AddressExtractor.withContext(getMailetContext())
                    .allowedSpecials(ALLOWED_SPECIALS)
                    .getSpecialAddress(to.get());
            if (specialAddress.isPresent()) {
                return specialAddress
                    .flatMap(MailAddress::toInternetAddress)
                    .map(ImmutableList::of)
                    .orElse(ImmutableList.of());
            }
            LOGGER.info("\"to\" parameter ignored, set to sender");
        }
        return SpecialAddress.SENDER
            .toInternetAddress()
            .map(ImmutableList::of)
            .orElse(ImmutableList.of());
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
    public Optional<MailAddress> getReversePath() throws MessagingException {
        return SpecialAddressesUtils.from(this)
                .getFirstSpecialAddressIfMatchingOrGivenAddress(getInitParameters().getReversePath(), RedirectNotify.REVERSE_PATH_ALLOWED_SPECIALS);
    }

    @Override
    public Optional<MailAddress> getReversePath(Mail originalMail) throws MessagingException {
        return getSender(originalMail);
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
        ProcessRedirectNotify.from(this).process(originalMail);
    }
}
