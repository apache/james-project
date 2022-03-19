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
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.redirect.AddressExtractor;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.ProcessRedirectNotify;
import org.apache.james.transport.mailets.redirect.RedirectMailetInitParameters;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.TypeCode;
import org.apache.james.transport.util.RecipientsUtils;
import org.apache.james.transport.util.ReplyToUtils;
import org.apache.james.transport.util.SenderUtils;
import org.apache.james.transport.util.TosUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Replaces incoming recipients with those specified, and resends the message
 * unaltered.
 * </p>
 * <p>
 * Can be totally replaced by an equivalent usage of {@link Resend} (see below),
 * simply replacing <i>&lt;forwardto&gt;</i> with <i>&lt;recipients&gt</i>.
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="Forward">
 *   &lt;forwardTo&gt;<i>comma delimited list of email addresses</i>&lt;/forwardTo&gt;
 *   &lt;passThrough&gt;<i>true or false, default=false</i>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false, default=true</i>&lt;/fakeDomainCheck&gt;
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
 *   &lt;forwardTo&gt;comma delimited list of email addresses&lt;/recipients&gt;
 *   &lt;passThrough&gt;true or false&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<i>true or false</i>&lt;/fakeDomainCheck&gt;
 *   &lt;debug&gt;<i>true or false</i>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * <p>
 * <i>forwardto</i> can be used instead of <i>forwardTo</i>; such name is kept
 * for backward compatibility.
 * </p>
 */
public class Forward extends GenericMailet implements RedirectNotify {
    private static final Logger LOGGER = LoggerFactory.getLogger(Forward.class);

    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of(
            "debug", "passThrough", "fakeDomainCheck", "forwardto", "forwardTo");
    private static final List<String> ALLOWED_SPECIALS = ImmutableList.of(
            "postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null");
    private final DNSService dns;

    @Inject
    Forward(DNSService dns) {
        this.dns = dns;
    }

    @Override
    public String getMailetInfo() {
        return "Forward Mailet";
    }

    @Override
    public InitParameters getInitParameters() {
        return RedirectMailetInitParameters.from(this, Optional.of(TypeCode.NONE));
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
        return getInitParameters().getMessage();
    }

    @Override
    public List<MailAddress> getRecipients() throws MessagingException {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        for (InternetAddress address : extractAddresses(getForwardTo())) {
            builder.add(toMailAddress(address));
        }
        return builder.build();
    }

    @Override
    public List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
        return RecipientsUtils.from(this).getRecipients(originalMail);
    }

    private InternetAddress[] extractAddresses(String addressList) throws MessagingException {
        try {
            return InternetAddress.parse(addressList, false);
        } catch (AddressException e) {
            throw new MessagingException("Exception thrown in getRecipients() parsing: " + addressList, e);
        }
    }

    private MailAddress toMailAddress(InternetAddress address) throws MessagingException {
        try {
            Optional<MailAddress> specialAddress = AddressExtractor.withContext(getMailetContext())
                    .allowedSpecials(ALLOWED_SPECIALS)
                    .getSpecialAddress(address.getAddress());
            if (specialAddress.isPresent()) {
                return specialAddress.get();
            }
            return new MailAddress(address);
        } catch (Exception e) {
            throw new MessagingException("Exception thrown in getRecipients() parsing: " + address.getAddress());
        }
    }

    private String getForwardTo() throws MessagingException {
        String forwardTo = getInitParameter("forwardto", getInitParameter("forwardTo"));
        if (Strings.isNullOrEmpty(forwardTo)) {
            throw new MessagingException("Failed to initialize \"recipients\" list: no or empty <forwardTo> or <forwardto> init parameter found");
        }
        return forwardTo;
    }

    @Override
    public List<InternetAddress> getTo() throws MessagingException {
        return ImmutableList.of();
    }

    @Override
    public List<MailAddress> getTo(Mail originalMail) throws MessagingException {
        return TosUtils.from(this).getTo(originalMail);
    }

    @Override
    public Optional<MailAddress> getReplyTo() throws MessagingException {
        return Optional.empty();
    }

    @Override
    public Optional<MailAddress> getReplyTo(Mail originalMail) throws MessagingException {
        return ReplyToUtils.from(getReplyTo()).getReplyTo(originalMail);
    }

    @Override
    public Optional<MailAddress> getReversePath() throws MessagingException {
        return Optional.empty();
    }

    @Override
    public Optional<MailAddress> getReversePath(Mail originalMail) throws MessagingException {
        return Optional.empty();
    }

    @Override
    public Optional<MailAddress> getSender() throws MessagingException {
        return Optional.empty();
    }

    @Override
    public Optional<MailAddress> getSender(Mail originalMail) throws MessagingException {
        return SenderUtils.from(getSender()).getSender(originalMail);
    }

    @Override
    public Optional<String> getSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        return Optional.empty();
    }

    @Override
    public void service(Mail originalMail) throws MessagingException {
        ProcessRedirectNotify.from(this).process(originalMail);
    }
}
