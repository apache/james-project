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

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.james.transport.mailets.redirect.AbstractRedirect;
import org.apache.james.transport.mailets.redirect.AddressExtractor;
import org.apache.james.transport.mailets.redirect.InitParameters;
import org.apache.james.transport.mailets.redirect.RedirectMailetInitParameters;
import org.apache.james.transport.mailets.redirect.TypeCode;
import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.james.transport.util.RecipientsUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

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
 *   &lt;recipients&gt;comma delimited list of email addresses&lt;/recipients&gt;
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
public class Forward extends AbstractRedirect {

    private static final String[] CONFIGURABLE_PARAMETERS = new String[] {
            "debug", "passThrough", "fakeDomainCheck", "forwardto", "forwardTo" };
    private static final List<String> ALLOWED_SPECIALS = ImmutableList.of(
            "postmaster", "sender", "from", "replyTo", "reversePath", "unaltered", "recipients", "to", "null");

    @Override
    public String getMailetInfo() {
        return "Forward Mailet";
    }

    @Override
    public InitParameters getInitParameters() {
        return RedirectMailetInitParameters.from(this, Optional.of(TypeCode.NONE), Optional.<TypeCode> absent());
    }

    @Override
    protected String[] getAllowedInitParameters() {
        return CONFIGURABLE_PARAMETERS;
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
    protected List<MailAddress> getRecipients(Mail originalMail) throws MessagingException {
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
    protected List<InternetAddress> getTo() throws MessagingException {
        return ImmutableList.of();
    }

    @Override
    protected MailAddress getReplyTo() throws MessagingException {
        return null;
    }

    @Override
    protected MailAddress getReversePath() throws MessagingException {
        return null;
    }

    @Override
    protected MailAddress getReversePath(Mail originalMail) throws MessagingException {
        return null;
    }

    @Override
    protected MailAddress getSender() throws MessagingException {
        return null;
    }

    @Override
    protected Optional<String> getSubjectPrefix(Mail newMail, String subjectPrefix, Mail originalMail) throws MessagingException {
        return Optional.absent();
    }

    @Override
    protected MimeMessageModifier getMimeMessageModifier(Mail newMail, Mail originalMail) throws MessagingException {
        return new MimeMessageModifier(newMail.getMessage());
    }
}
