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

package org.apache.james.mailbox.elasticsearch.json;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mailbox.store.search.comparator.SentDateComparator;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.MimeUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class HeaderCollection {

    public static class Builder {

        private final Set<EMailer> toAddressSet;
        private final Set<EMailer> fromAddressSet;
        private final Set<EMailer> ccAddressSet;
        private final Set<EMailer> bccAddressSet;
        private final Set<EMailer> replyToAddressSet;
        private final Set<String> subjectSet;
        private final Multimap<String, String> headers;
        private Optional<ZonedDateTime> sentDate;

        private Builder() {
            toAddressSet = new HashSet<>();
            fromAddressSet = new HashSet<>();
            ccAddressSet = new HashSet<>();
            bccAddressSet = new HashSet<>();
            replyToAddressSet = new HashSet<>();
            subjectSet = new HashSet<>();
            headers = ArrayListMultimap.create();
            sentDate = Optional.empty();
        }

        public Builder add(Field field) {
            Preconditions.checkNotNull(field);
            String headerName = field.getName().toLowerCase(Locale.US);
            String sanitizedValue = MimeUtil.unscrambleHeaderValue(field.getBody());

            headers.put(headerName, sanitizedValue);
            handleSpecificHeader(headerName, sanitizedValue);
            return this;
        }

        public HeaderCollection build() {
            return new HeaderCollection(
                ImmutableSet.copyOf(toAddressSet),
                ImmutableSet.copyOf(fromAddressSet),
                ImmutableSet.copyOf(ccAddressSet),
                ImmutableSet.copyOf(bccAddressSet),
                ImmutableSet.copyOf(replyToAddressSet),
                ImmutableSet.copyOf(subjectSet),
                ImmutableMultimap.copyOf(headers),
                sentDate);
        }

        private void handleSpecificHeader(String headerName, String headerValue) {
            switch (headerName) {
                case TO:
                case FROM:
                case CC:
                case BCC:
                case REPLY_TO:
                    manageAddressField(headerName, headerValue);
                    break;
                case SUBJECT:
                    subjectSet.add(headerValue);
                    break;
                case DATE:
                    sentDate = SentDateComparator.toISODate(headerValue);
                    break;
            }
        }

        private void manageAddressField(String headerName, String headerValue) {
            LenientAddressParser.DEFAULT
                .parseAddressList(headerValue)
                .stream()
                .flatMap(this::convertAddressToMailboxStream)
                .map((mailbox) -> new EMailer(SearchUtil.getDisplayAddress(mailbox) , mailbox.getAddress()))
                .collect(Collectors.toCollection(() -> getAddressSet(headerName)));
        }

        private Stream<Mailbox> convertAddressToMailboxStream(Address address) {
            if (address instanceof Mailbox) {
                return Stream.of((Mailbox) address);
            } else if (address instanceof Group) {
                return ((Group) address).getMailboxes().stream();
            }
            return Stream.empty();
        }

        private Set<EMailer> getAddressSet(String headerName) {
            switch (headerName) {
                case TO:
                    return toAddressSet;
                case FROM:
                    return fromAddressSet;
                case CC:
                    return ccAddressSet;
                case BCC:
                    return bccAddressSet;
                case REPLY_TO:
                    return replyToAddressSet;
            }
            throw new RuntimeException(headerName + " is not a address header name");
        }
    }

    public static final String TO = "to";
    public static final String FROM = "from";
    public static final String CC = "cc";
    public static final String BCC = "bcc";
    public static final String REPLY_TO = "reply-to";
    public static final String SUBJECT = "subject";
    public static final String DATE = "date";

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableSet<EMailer> toAddressSet;
    private final ImmutableSet<EMailer> fromAddressSet;
    private final ImmutableSet<EMailer> ccAddressSet;
    private final ImmutableSet<EMailer> bccAddressSet;
    private final ImmutableSet<EMailer> replyToAddressSet;
    private final ImmutableSet<String> subjectSet;
    private final ImmutableMultimap<String, String> headers;
    private final Optional<ZonedDateTime> sentDate;

    private HeaderCollection(ImmutableSet<EMailer> toAddressSet, ImmutableSet<EMailer> fromAddressSet,
        ImmutableSet<EMailer> ccAddressSet, ImmutableSet<EMailer> bccAddressSet, ImmutableSet<EMailer> replyToAddressSet, ImmutableSet<String> subjectSet,
        ImmutableMultimap<String, String> headers, Optional<ZonedDateTime> sentDate) {
        this.toAddressSet = toAddressSet;
        this.fromAddressSet = fromAddressSet;
        this.ccAddressSet = ccAddressSet;
        this.bccAddressSet = bccAddressSet;
        this.replyToAddressSet = replyToAddressSet;
        this.subjectSet = subjectSet;
        this.headers = headers;
        this.sentDate = sentDate;
    }

    public Set<EMailer> getToAddressSet() {
        return toAddressSet;
    }

    public Set<EMailer> getFromAddressSet() {
        return fromAddressSet;
    }

    public Set<EMailer> getCcAddressSet() {
        return ccAddressSet;
    }

    public Set<EMailer> getBccAddressSet() {
        return bccAddressSet;
    }

    public Set<EMailer> getReplyToAddressSet() {
        return replyToAddressSet;
    }

    public Set<String> getSubjectSet() {
        return subjectSet;
    }

    public Optional<ZonedDateTime> getSentDate() {
        return sentDate;
    }

    public Multimap<String, String> getHeaders() {
        return headers;
    }

}
