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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
import org.apache.james.mime4j.stream.ParserCursor;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;

public final class EnvelopeBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvelopeBuilder.class);
    private static final CharMatcher PRINTABLE_ASCII = CharMatcher.inRange(' ', '~').or(CharMatcher.is('\t'));
    private static final CharMatcher LATIN_1 = CharMatcher.inRange((char) 0, (char) 0xFF);

    public FetchResponse.Envelope buildEnvelope(Headers headers) throws MailboxException {
        final String date = headerValue(headers, ImapConstants.RFC822_DATE);
        final String subject = encodeNonAscii(headerValue(headers, ImapConstants.RFC822_SUBJECT));
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

    /**
     * Raw UTF-8 header values (RFC 6532) are sent as encoded-words, which every client understands.
     */
    private String encodeNonAscii(String value) {
        if (value == null || PRINTABLE_ASCII.matchesAllOf(value)) {
            return value;
        }
        return EncoderUtil.encodeEncodedWord(value, EncoderUtil.Usage.TEXT_TOKEN);
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
        // LenientAddressParser.parseAddressList(String) bounds the parse with the number of chars of the value while
        // it parses its UTF-8 bytes: non ASCII values lost their last characters
        ByteSequence raw = ContentUtil.encode(StandardCharsets.UTF_8, value);
        AddressList addressList = LenientAddressParser.DEFAULT.parseAddressList(raw, new ParserCursor(0, raw.length()));
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
        final String domain = fromParsedBytes(mailbox.getDomain());
        final DomainList route = mailbox.getRoute();
        final String atDomainList;
        if (route == null || route.size() == 0) {
            atDomainList = null;
        } else {
            atDomainList = route.toRouteString();
        }
        final String localPart = fromParsedBytes(mailbox.getLocalPart());
        return buildMailboxAddress(name, atDomainList, localPart, domain);
    }

    /**
     * The address parser reads the UTF-8 bytes of the value one char per byte: turn them back into chars.
     * Strings holding other chars (eg decoded encoded-words) or that are not valid UTF-8 are kept.
     */
    private String fromParsedBytes(String parsed) {
        if (parsed == null || PRINTABLE_ASCII.matchesAllOf(parsed) || !LATIN_1.matchesAllOf(parsed)) {
            return parsed;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(parsed.getBytes(StandardCharsets.ISO_8859_1)))
                .toString();
        } catch (CharacterCodingException e) {
            return parsed;
        }
    }

    private String encodedMailboxName(Mailbox mailbox) {
        String name = fromParsedBytes(mailbox.getName());
        if (name == null) {
            return null;
        }
        // The IMAP string carries the quoting: do not use the RFC 5322 quoted-string form of the display name
        if (PRINTABLE_ASCII.matchesAllOf(name)) {
            return name;
        }
        // Encode non ASCII names
        // See IMAP-266
        return EncoderUtil.encodeEncodedWord(name, EncoderUtil.Usage.WORD_ENTITY);
    }

    private void addAddresses(Group group, List<FetchResponse.Envelope.Address> addresses) {
        final String groupName = fromParsedBytes(group.getName());
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