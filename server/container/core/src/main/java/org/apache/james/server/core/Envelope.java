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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.util.StreamUtils;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class Envelope {
    public interface ValidationPolicy {
        ValidationPolicy THROW = e -> {
            throw new RuntimeException(e);
        };
        ValidationPolicy IGNORE = e -> {
            LoggerFactory.getLogger(Envelope.class).info("Failed to parse a mail address", e);
            return Optional.empty();
        };

        Optional<MailAddress> handleParsingException(AddressException e);
    }

    public static Envelope fromMime4JMessage(org.apache.james.mime4j.dom.Message mime4JMessage) {
        return fromMime4JMessage(mime4JMessage, ValidationPolicy.THROW);
    }

    public static Envelope fromMime4JMessage(org.apache.james.mime4j.dom.Message mime4JMessage, ValidationPolicy validationPolicy) {
        MaybeSender sender = Optional.ofNullable(mime4JMessage.getFrom())
            .map(MailboxList::stream)
            .orElse(Stream.empty())
            .findAny()
            .map(Mailbox::getAddress)
            .flatMap(addressAsString -> newMailAddress(validationPolicy, addressAsString))
            .map(MaybeSender::of)
            .orElse(MaybeSender.nullSender());

        Stream<MailAddress> to = emailersToMailAddresses(mime4JMessage.getTo(), validationPolicy);
        Stream<MailAddress> cc = emailersToMailAddresses(mime4JMessage.getCc(), validationPolicy);
        Stream<MailAddress> bcc = emailersToMailAddresses(mime4JMessage.getBcc(), validationPolicy);

        return new Envelope(sender,
            StreamUtils.flatten(Stream.of(to, cc, bcc))
                .collect(ImmutableSet.toImmutableSet()));
    }

    private static Optional<MailAddress> newMailAddress(ValidationPolicy validationPolicy, String addressAsString) {
        try {
            if (addressAsString.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new MailAddress(addressAsString));
        } catch (AddressException e) {
            return validationPolicy.handleParsingException(e);
        }
    }

    private static Stream<MailAddress> emailersToMailAddresses(AddressList addresses, ValidationPolicy validationPolicy) {
        return Optional.ofNullable(addresses)
            .map(AddressList::flatten)
            .map(MailboxList::stream)
            .orElse(Stream.of())
            .map(Mailbox::getAddress)
            .map(addressAsString -> newMailAddress(validationPolicy, addressAsString))
            .flatMap(Optional::stream);
    }

    private final MaybeSender from;
    private final Set<MailAddress> recipients;

    public Envelope(MaybeSender from, Set<MailAddress> recipients) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(recipients);

        this.from = from;
        this.recipients = recipients;
    }

    public MaybeSender getFrom() {
        return from;
    }

    public Set<MailAddress> getRecipients() {
        return recipients;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Envelope) {
            Envelope envelope = (Envelope) o;

            return Objects.equals(this.from, envelope.from)
                && Objects.equals(this.recipients, envelope.recipients);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(from, recipients);
    }
}
