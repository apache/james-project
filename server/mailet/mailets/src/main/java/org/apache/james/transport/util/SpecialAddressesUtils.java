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

import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.ParseException;

import org.apache.james.transport.mailets.redirect.AddressExtractor;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.transport.mailets.redirect.SpecialAddressKind;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class SpecialAddressesUtils {

    public static SpecialAddressesUtils from(GenericMailet genericMailet) {
        return new SpecialAddressesUtils(genericMailet);
    }

    private final GenericMailet genericMailet;

    public SpecialAddressesUtils(GenericMailet genericMailet) {
        this.genericMailet = genericMailet;
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
            if (!SpecialAddress.isSpecialAddress(mailAddress)) {
                builder.add(mailAddress);
                continue;
            }

            SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
            if (specialAddressKind == null) {
                builder.add(mailAddress);
                continue;
            }
            switch (specialAddressKind) {
            case SENDER:
            case FROM:
                MailAddress sender = mailWithReplacementAddresses.getSender();
                if (sender != null) {
                    builder.add(sender);
                }
                break;
            case REPLY_TO:
                builder.addAll(getReplyTosFromMail(mailWithReplacementAddresses));
                break;
            case REVERSE_PATH:
                MailAddress reversePath = mailWithReplacementAddresses.getSender();
                if (reversePath != null) {
                    builder.add(reversePath);
                }
                break;
            case RECIPIENTS:
            case TO:
                builder.addAll(mailWithReplacementAddresses.getRecipients());
                break;
            case UNALTERED:
            case NULL:
                break;
            case DELETE:
                builder.add(mailAddress);
                break;
            }
        }
        return builder.build();
    }

    private Set<MailAddress> getReplyTosFromMail(Mail mail) {
        try {
            InternetAddress[] replyToArray = (InternetAddress[]) mail.getMessage().getReplyTo();
            if (replyToArray == null || replyToArray.length == 0) {
                return getSender(mail);
            }
            return getReplyTos(replyToArray);
        } catch (MessagingException ae) {
            genericMailet.log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
            return ImmutableSet.of();
        }
    }

    private Set<MailAddress> getSender(Mail mail) {
        MailAddress sender = mail.getSender();
        if (sender != null) {
            return ImmutableSet.of(sender);
        }
        return ImmutableSet.of();
    }

    private Set<MailAddress> getReplyTos(InternetAddress[] replyToArray) {
        ImmutableSet.Builder<MailAddress> builder = ImmutableSet.builder();
        for (InternetAddress replyTo : replyToArray) {
            try {
                builder.add(new MailAddress(replyTo));
            } catch (ParseException pe) {
                genericMailet.log("Unable to parse a \"REPLY_TO\" header address in the original message: " + replyTo + "; ignoring.");
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
            MailAddress mailAddress = new MailAddress(internetAddress);
            if (!SpecialAddress.isSpecialAddress(mailAddress)) {
                builder.add(new MailAddress(internetAddress));
                continue;
            }

            SpecialAddressKind specialAddressKind = SpecialAddressKind.forValue(mailAddress.getLocalPart());
            if (specialAddressKind == null) {
                builder.add(mailAddress);
                continue;
            }

            switch (specialAddressKind) {
            case SENDER:
                MailAddress sender = mailWithReplacementAddresses.getSender();
                if (sender != null) {
                    builder.add(sender);
                }
                break;
            case REVERSE_PATH:
                MailAddress reversePath = mailWithReplacementAddresses.getSender();
                if (reversePath != null) {
                    builder.add(reversePath);
                }
                break;
            case FROM:
                try {
                    InternetAddress[] fromArray = (InternetAddress[]) mailWithReplacementAddresses.getMessage().getFrom();
                    builder.addAll(allOrSender(mailWithReplacementAddresses, fromArray));
                } catch (MessagingException me) {
                    genericMailet.log("Unable to parse the \"FROM\" header in the original message; ignoring.");
                }
                break;
            case REPLY_TO:
                try {
                    InternetAddress[] replyToArray = (InternetAddress[]) mailWithReplacementAddresses.getMessage().getReplyTo();
                    builder.addAll(allOrSender(mailWithReplacementAddresses, replyToArray));
                } catch (MessagingException me) {
                    genericMailet.log("Unable to parse the \"REPLY_TO\" header in the original message; ignoring.");
                }
                break;
            case TO:
            case RECIPIENTS:
                builder.addAll(toHeaders(mailWithReplacementAddresses));
                break;
            case NULL:
            case UNALTERED:
                break;
            case DELETE:
                builder.add(new MailAddress(internetAddress));
                break;
            }
        }
        return builder.build();
    }

    private List<MailAddress> allOrSender(Mail mail, InternetAddress[] addresses) throws AddressException {
        if (addresses != null) {
            return MailAddressUtils.from(addresses);
        } else {
            MailAddress reversePath = mail.getSender();
            if (reversePath != null) {
                return ImmutableList.of(reversePath);
            }
        }
        return ImmutableList.of();
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
                        genericMailet.log("Unable to parse a \"TO\" header address in the original message: " + toHeader + "; ignoring.");
                    }
                }
            }
            return ImmutableList.of();
        } catch (MessagingException ae) {
            genericMailet.log("Unable to parse the \"TO\" header  in the original message; ignoring.");
            return ImmutableList.of();
        }
    }

    /**
     * If the givenAddress matches one of the allowedSpecials SpecialAddresses, then it's returned
     * else the givenAddress is returned.
     */
    public MailAddress getFirstSpecialAddressIfMatchingOrGivenAddress(String givenAddress, List<String> allowedSpecials) throws MessagingException {
        if (Strings.isNullOrEmpty(givenAddress)) {
            return null;
        }

        List<MailAddress> extractAddresses = AddressExtractor
                .withContext(genericMailet.getMailetContext())
                .allowedSpecials(allowedSpecials)
                .extract(givenAddress);
        return FluentIterable.from(extractAddresses).first().orNull();
    }
}
