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
package org.apache.james.transport.util;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.ParseException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.AddressExtractor;
import org.apache.james.transport.mailets.redirect.RedirectNotify;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.transport.mailets.redirect.SpecialAddressKind;
import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class SpecialAddressesUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialAddressesUtils.class);

    public static SpecialAddressesUtils from(RedirectNotify mailet) {
        return new SpecialAddressesUtils(mailet);
    }

    private final RedirectNotify mailet;

    public SpecialAddressesUtils(RedirectNotify genericMailet) {
        this.mailet = genericMailet;
    }

    /**
     * Returns a new Collection built over <i>mailAddresses</i> replacing special
     * addresses with real <code>MailAddress</code>-es.<br>
     * Manages <code>SpecialAddress.SENDER</code>,
     * <code>SpecialAddress.REVERSE_PATH</code>,
     * <code>SpecialAddress.FROM</code>, <code>SpecialAddress.REPLY_TO</code>,
     * <code>SpecialAddress.RECIPIENTS</code>, <code>SpecialAddress.TO</code>,
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code>.<br>
     * <code>SpecialAddress.FROM</code> is made equivalent to
     * <code>SpecialAddress.SENDER</code>; <code>SpecialAddress.TO</code> is
     * made equivalent to <code>SpecialAddress.RECIPIENTS</code>.<br>
     * <code>SpecialAddress.REPLY_TO</code> uses the ReplyTo header if
     * available, otherwise the From header if available, otherwise the Sender
     * header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code> are ignored.<br>
     * Any other address is not replaced.
     */
    public List<MailAddress> replaceSpecialAddresses(Mail mailWithReplacementAddresses, List<MailAddress> mailAddresses) {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        for (MailAddress mailAddress : mailAddresses) {
            builder.addAll(getCorrespondingAddress(mailWithReplacementAddresses, mailAddress));
        }
        return builder.build();
    }

    private Collection<MailAddress> getCorrespondingAddress(Mail mail, MailAddress mailAddress) {
        if (!SpecialAddress.isSpecialAddress(mailAddress)) {
            return ImmutableSet.of(mailAddress);
        }

        SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
        if (specialAddressKind == null) {
            return ImmutableSet.of(mailAddress);
        }
        switch (specialAddressKind) {
            case SENDER:
            case FROM:
            case REVERSE_PATH:
                return mail.getMaybeSender().asOptional()
                    .map(ImmutableSet::of)
                    .orElse(ImmutableSet.of());
            case REPLY_TO:
                return getReplyTosFromMail(mail);
            case RECIPIENTS:
            case TO:
                return mail.getRecipients();
            case UNALTERED:
            case NULL:
                break;
            case DELETE:
                return ImmutableSet.of(mailAddress);
        }
        return ImmutableList.of();
    }

    private Set<MailAddress> getReplyTosFromMail(Mail mail) {
        try {
            InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
            if (replyToArray == null || replyToArray.length == 0) {
                return getSender(mail);
            }
            return getReplyTos(replyToArray);
        } catch (MessagingException ae) {
            LOGGER.warn("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
            return ImmutableSet.of();
        }
    }

    private Set<MailAddress> getSender(Mail mail) {
        return mail.getMaybeSender().asStream().collect(ImmutableSet.toImmutableSet());
    }

    private Set<MailAddress> getReplyTos(InternetAddress[] replyToArray) {
        ImmutableSet.Builder<MailAddress> builder = ImmutableSet.builder();
        for (InternetAddress replyTo : replyToArray) {
            try {
                builder.add(new MailAddress(replyTo));
            } catch (ParseException pe) {
                LOGGER.warn("Unable to parse a \"REPLY_TO\" header address in the original message: {}; ignoring.", replyTo);
            }
        }
        return builder.build();
    }

    /**
     * Returns a new Collection built over <i>list</i> replacing special
     * addresses with real <code>InternetAddress</code>-es.<br>
     * Manages <code>SpecialAddress.SENDER</code>,
     * <code>SpecialAddress.REVERSE_PATH</code>,
     * <code>SpecialAddress.FROM</code>, <code>SpecialAddress.REPLY_TO</code>,
     * <code>SpecialAddress.RECIPIENTS</code>, <code>SpecialAddress.TO</code>,
     * <code>SpecialAddress.NULL</code> and
     * <code>SpecialAddress.UNALTERED</code>.<br>
     * <code>SpecialAddress.RECIPIENTS</code> is made equivalent to
     * <code>SpecialAddress.TO</code>.<br>
     * <code>SpecialAddress.FROM</code> uses the From header if available,
     * otherwise the Sender header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.REPLY_TO</code> uses the ReplyTo header if
     * available, otherwise the From header if available, otherwise the Sender
     * header if available, otherwise the return-path.<br>
     * <code>SpecialAddress.UNALTERED</code> is ignored.<br>
     * Any other address is not replaced.<br>
     */
    public List<MailAddress> replaceInternetAddresses(Mail mailWithReplacementAddresses, List<InternetAddress> internetAddresses) throws MessagingException {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        for (InternetAddress internetAddress : internetAddresses) {
            builder.addAll(getCorrespondingAddress(internetAddress, mailWithReplacementAddresses));
        }
        return builder.build();
    }

    private Collection<MailAddress> getCorrespondingAddress(InternetAddress internetAddress, Mail mail) throws AddressException {
        MailAddress mailAddress = new MailAddress(internetAddress);
        if (!SpecialAddress.isSpecialAddress(mailAddress)) {
            return ImmutableSet.of(new MailAddress(internetAddress));
        }

        SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
        if (specialAddressKind == null) {
            return ImmutableSet.of(new MailAddress(internetAddress));
        }

        switch (specialAddressKind) {
            case SENDER:
            case REVERSE_PATH:
                return getSender(mail);
            case FROM:
                try {
                    InternetAddress[] fromArray = (InternetAddress[]) mail.getMessage().getFrom();
                    return allOrSender(mail, fromArray);
                } catch (MessagingException me) {
                    LOGGER.warn("Unable to parse the \"FROM\" header in the original message; ignoring.");
                    return ImmutableSet.of();
                }
            case REPLY_TO:
                try {
                    InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
                    return allOrSender(mail, replyToArray);
                } catch (MessagingException me) {
                    LOGGER.warn("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
                    return ImmutableSet.of();
                }
            case TO:
            case RECIPIENTS:
                return toHeaders(mail);
            case NULL:
            case UNALTERED:
                return ImmutableList.of();
            case DELETE:
                return ImmutableSet.of(new MailAddress(internetAddress));
        }
        return ImmutableList.of();
    }

    private List<MailAddress> allOrSender(Mail mail, InternetAddress[] addresses) throws AddressException {
        if (addresses != null) {
            return MailAddressUtils.from(addresses);
        } else {
            return mail.getMaybeSender().asList();
        }
    }

    private List<MailAddress> toHeaders(Mail mail) {
        try {
            String[] toHeaders = mail.getMessage().getHeader(RFC2822Headers.TO);
            if (toHeaders != null) {
                for (String toHeader : toHeaders) {
                    try {
                        InternetAddress[] originalToInternetAddresses = InternetAddress.parse(toHeader, false);
                        return MailAddressUtils.from(originalToInternetAddresses);
                    } catch (MessagingException ae) {
                        LOGGER.warn("Unable to parse a \"TO\" header address in the original message: {}; ignoring.", toHeader);
                    }
                }
            }
            return ImmutableList.of();
        } catch (MessagingException ae) {
            LOGGER.warn("Unable to parse the \"TO\" header  in the original message; ignoring.");
            return ImmutableList.of();
        }
    }

    /**
     * If the givenAddress matches one of the allowedSpecials SpecialAddresses, then it's returned
     * else the givenAddress is returned.
     */
    public Optional<MailAddress> getFirstSpecialAddressIfMatchingOrGivenAddress(Optional<String> givenAddress, List<String> allowedSpecials) throws MessagingException {
        List<MailAddress> extractAddresses = AddressExtractor
                .withContext(mailet.getMailetContext())
                .allowedSpecials(allowedSpecials)
                .extract(givenAddress);
        return extractAddresses.stream()
            .findFirst();
    }
}
