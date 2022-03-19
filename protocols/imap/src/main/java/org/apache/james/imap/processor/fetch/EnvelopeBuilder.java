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

/**
 * 
 */
package org.apache.james.imap.processor.fetch;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import jakarta.mail.internet.MimeUtility;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.DomainList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnvelopeBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvelopeBuilder.class);

    public FetchResponse.Envelope buildEnvelope(Headers headers) throws MailboxException {
        final String date = headerValue(headers, ImapConstants.RFC822_DATE);
        final String subject = headerValue(headers, ImapConstants.RFC822_SUBJECT);
        final FetchResponse.Envelope.Address[] fromAddresses = buildAddresses(headers, ImapConstants.RFC822_FROM);
        final FetchResponse.Envelope.Address[] senderAddresses = buildAddresses(headers, ImapConstants.RFC822_SENDER, fromAddresses);
        final FetchResponse.Envelope.Address[] replyToAddresses = buildAddresses(headers, ImapConstants.RFC822_REPLY_TO, fromAddresses);
        final FetchResponse.Envelope.Address[] toAddresses = buildAddresses(headers, ImapConstants.RFC822_TO);
        final FetchResponse.Envelope.Address[] ccAddresses = buildAddresses(headers, ImapConstants.RFC822_CC);
        final FetchResponse.Envelope.Address[] bccAddresses = buildAddresses(headers, ImapConstants.RFC822_BCC);
        final String inReplyTo = headerValue(headers, ImapConstants.RFC822_IN_REPLY_TO);
        final String messageId = headerValue(headers, ImapConstants.RFC822_MESSAGE_ID);
        return new EnvelopeImpl(date, subject, fromAddresses, senderAddresses, replyToAddresses, toAddresses, ccAddresses, bccAddresses, inReplyTo, messageId);
    }

    private String headerValue(Headers message, String headerName) throws MailboxException {
        final Header header = MessageResultUtils.getMatching(headerName, message.headers());
        if (header == null) {
            return null;
        }
        final String value = header.getValue();
        if (value == null || "".equals(value)) {
            return null;
        }
        // ENVELOPE header values must be unfolded
        // See IMAP-269
        //
        //
        // IMAP-Servers are advised to also replace tabs with single spaces while doing the unfolding. This is what javamails
        // unfold does. mime4j's unfold does strictly follow the rfc and so preserve them
        //
        // See IMAP-327 and https://mailman2.u.washington.edu/mailman/htdig/imap-protocol/2010-July/001271.html
        return MimeUtility.unfold(value);
    }

    private FetchResponse.Envelope.Address[] buildAddresses(Headers message, String headerName, FetchResponse.Envelope.Address[] defaults) throws MailboxException {
        final FetchResponse.Envelope.Address[] addresses = buildAddresses(message, headerName);
        if (addresses == null) {
            return defaults;
        }
        return addresses;
    }

    /**
     * Try to parse the addresses out of the header. If its not possible because
     * of a {@link ParseException} a null value is returned
     *
     * @return addresses
     */
    private FetchResponse.Envelope.Address[] buildAddresses(Headers message, String headerName) throws MailboxException {
        final Header header = MessageResultUtils.getMatching(headerName, message.headers());
        if (header == null) {
            return null;
        }
        // We need to unfold the header line.
        // See https://issues.apache.org/jira/browse/IMAP-154
        //
        // IMAP-Servers are advised to also replace tabs with single spaces while doing the unfolding. This is what javamails
        // unfold does. mime4j's unfold does strictly follow the rfc and so preserve them
        //
        // See IMAP-327 and https://mailman2.u.washington.edu/mailman/htdig/imap-protocol/2010-July/001271.html
        String value = MimeUtility.unfold(header.getValue());
        if ("".equals(value.trim())) {
            return null;
        }
        AddressList addressList = LenientAddressParser.DEFAULT.parseAddressList(value);
        final int size = addressList.size();
        final List<FetchResponse.Envelope.Address> addresses = new ArrayList<>(size);
        for (Address address : addressList) {
            if (address instanceof Group) {
                final Group group = (Group) address;
                addAddresses(group, addresses);

            } else if (address instanceof Mailbox) {
                final Mailbox mailbox = (Mailbox) address;
                final FetchResponse.Envelope.Address mailboxAddress = buildMailboxAddress(mailbox);
                addresses.add(mailboxAddress);

            } else {
                LOGGER.warn("Unknown address type {}", address.getClass());
            }
        }
        return addresses.toArray(FetchResponse.Envelope.Address[]::new);

    }

    private FetchResponse.Envelope.Address buildMailboxAddress(org.apache.james.mime4j.dom.address.Mailbox mailbox) {
        final String name = encodedMailboxName(mailbox);
        final String domain = mailbox.getDomain();
        final DomainList route = mailbox.getRoute();
        final String atDomainList;
        if (route == null || route.size() == 0) {
            atDomainList = null;
        } else {
            atDomainList = route.toRouteString();
        }
        final String localPart = mailbox.getLocalPart();
        return buildMailboxAddress(name, atDomainList, localPart, domain);
    }

    private String encodedMailboxName(Mailbox mailbox) {
        // Encode the mailbox name
        // See IMAP-266
        String name = mailbox.getName();
        if (name != null) {
            return EncoderUtil.encodeAddressDisplayName(name);
        }
        return null;
    }

    private void addAddresses(Group group, List<FetchResponse.Envelope.Address> addresses) {
        final String groupName = group.getName();
        final FetchResponse.Envelope.Address start = startGroup(groupName);
        addresses.add(start);
        final MailboxList mailboxList = group.getMailboxes();
        for (Mailbox mailbox : mailboxList) {
            final FetchResponse.Envelope.Address address = buildMailboxAddress(mailbox);
            addresses.add(address);
        }
        final FetchResponse.Envelope.Address end = endGroup();
        addresses.add(end);
    }

    private FetchResponse.Envelope.Address startGroup(String groupName) {
        return new AddressImpl(null, null, groupName, null);
    }

    private FetchResponse.Envelope.Address endGroup() {
        return new AddressImpl(null, null, null, null);
    }

    private FetchResponse.Envelope.Address buildMailboxAddress(String name, String atDomainList, String mailbox, String domain) {
        return new AddressImpl(atDomainList, domain, mailbox, name);
    }
}