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

package org.apache.james.server.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.ParserCursor;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AddressFields {
    /**
     * Parses an address field (From, Sender...) for an authorization check.
     *
     * jakarta.mail ignores what follows the first mailbox ('&lt;bob@domain.tld&gt; alice@domain.tld' reads as
     * bob@domain.tld) and stops at comments ('bob@domain.tld(comment).evil' reads as bob@domain.tld) while
     * mime4j, used for instance by JMAP Email/get, reads alice@domain.tld and bob@domain.tld.evil.
     *
     * @return the addresses jakarta.mail reads strictly, followed by the mailboxes mime4j reads in addition,
     * so that a check covers what any of them displays.
     */
    public static List<InternetAddress> parse(String field) throws AddressException {
        String unfolded = MimeUtility.unfold(field);
        InternetAddress[] jakartaAddresses = InternetAddress.parseHeader(unfolded, true);
        Set<String> jakartaMailboxes = Arrays.stream(jakartaAddresses)
            .flatMap(AddressFields::flatten)
            .map(address -> address.getAddress().toLowerCase(Locale.US))
            .collect(ImmutableSet.toImmutableSet());

        return ImmutableList.<InternetAddress>builder()
            .add(jakartaAddresses)
            .addAll(mime4jMailboxes(unfolded)
                .filter(address -> !jakartaMailboxes.contains(address.toLowerCase(Locale.US)))
                .distinct()
                .map(AddressFields::asInternetAddress)
                .toList())
            .build();
    }

    private static Stream<InternetAddress> flatten(InternetAddress address) {
        if (!address.isGroup()) {
            return Stream.of(address);
        }
        try {
            return Arrays.stream(address.getGroup(false));
        } catch (AddressException e) {
            return Stream.empty();
        }
    }

    private static Stream<String> mime4jMailboxes(String field) {
        try {
            // LenientAddressParser::parseAddressList(CharSequence) stops after field.length() bytes of the encoded
            // field, leaving the end of a field holding non-ASCII characters unread
            ByteSequence raw = ContentUtil.encode(field);
            return LenientAddressParser.DEFAULT.parseAddressList(raw, new ParserCursor(0, raw.length()))
                .flatten()
                .stream()
                .map(Mailbox::getAddress);
        } catch (RuntimeException e) {
            return Stream.empty();
        }
    }

    private static InternetAddress asInternetAddress(String address) {
        InternetAddress internetAddress = new InternetAddress();
        internetAddress.setAddress(address);
        return internetAddress;
    }
}
