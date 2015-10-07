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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.MimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HeaderCollection {

    public static class Builder {

        // Some sent e-mail have this form : Wed,  3 Jun 2015 09:05:46 +0000 (UTC)
        // Java 8 Time library RFC_1123_DATE_TIME corresponds to Wed,  3 Jun 2015 09:05:46 +0000 only
        // This REGEXP is here to match ( in order to remove ) the possible invalid end of a header date
        // Example of matching patterns :
        //  (UTC)
        //  (CEST)
        private static final Pattern DATE_SANITIZING_PATTERN = Pattern.compile(" *\\(.*\\) *");

        private final Set<EMailer> toAddressSet;
        private final Set<EMailer> fromAddressSet;
        private final Set<EMailer> ccAddressSet;
        private final Set<EMailer> bccAddressSet;
        private final Set<String> subjectSet;
        private final Multimap<String, String> headers;
        private Optional<ZonedDateTime> sentDate;

        private Builder() {
            toAddressSet = new HashSet<>();
            fromAddressSet = new HashSet<>();
            ccAddressSet = new HashSet<>();
            bccAddressSet = new HashSet<>();
            subjectSet = new HashSet<>();
            headers = ArrayListMultimap.create();
            sentDate = Optional.empty();
        }

        public Builder add(Field field) {
            Preconditions.checkNotNull(field);
            String headerName = field.getName().toLowerCase();
            String headerValue = field.getBody();
            headers.put(headerName, headerValue);
            handleSpecificHeader(headerName, headerValue);
            return this;
        }

        public HeaderCollection build() {
            return new HeaderCollection(
                ImmutableSet.copyOf(toAddressSet),
                ImmutableSet.copyOf(fromAddressSet),
                ImmutableSet.copyOf(ccAddressSet),
                ImmutableSet.copyOf(bccAddressSet),
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
                    manageAddressField(headerName, headerValue);
                    break;
                case SUBJECT:
                    subjectSet.add(headerValue);
                    break;
                case DATE:
                    sentDate = toISODate(headerValue);
                    break;
            }
        }

        private void manageAddressField(String headerName, String headerValue) {
            LenientAddressParser.DEFAULT
                .parseAddressList(MimeUtil.unfold(headerValue))
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
            }
            throw new RuntimeException(headerName + " is not a address header name");
        }

        private Optional<ZonedDateTime> toISODate(String value) {
            try {
                return Optional.of(ZonedDateTime.parse(
                    sanitizeDateStringHeaderValue(value),
                    DateTimeFormatter.RFC_1123_DATE_TIME));
            } catch (Exception e) {
                LOGGER.info("Can not parse receive date " + value);
                return Optional.empty();
            }
        }

        @VisibleForTesting String sanitizeDateStringHeaderValue(String value) {
            // Some sent e-mail have this form : Wed,  3 Jun 2015 09:05:46 +0000 (UTC)
            // Java 8 Time library RFC_1123_DATE_TIME corresponds to Wed,  3 Jun 2015 09:05:46 +0000 only
            // This method is here to convert the first date into something parsable by RFC_1123_DATE_TIME DateTimeFormatter
            Matcher sanitizerMatcher = DATE_SANITIZING_PATTERN.matcher(value);
            if (sanitizerMatcher.find()) {
                return value.substring(0 , sanitizerMatcher.start());
            }
            return value;
        }

    }

    public static final String TO = "to";
    public static final String FROM = "from";
    public static final String CC = "cc";
    public static final String BCC = "bcc";
    public static final String SUBJECT = "subject";
    public static final String DATE = "date";

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderCollection.class);

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableSet<EMailer> toAddressSet;
    private final ImmutableSet<EMailer> fromAddressSet;
    private final ImmutableSet<EMailer> ccAddressSet;
    private final ImmutableSet<EMailer> bccAddressSet;
    private final ImmutableSet<String> subjectSet;
    private final ImmutableMultimap<String, String> headers;
    private Optional<ZonedDateTime> sentDate;

    private HeaderCollection(ImmutableSet<EMailer> toAddressSet, ImmutableSet<EMailer> fromAddressSet,
        ImmutableSet<EMailer> ccAddressSet, ImmutableSet<EMailer> bccAddressSet, ImmutableSet<String> subjectSet,
        ImmutableMultimap<String, String> headers, Optional<ZonedDateTime> sentDate) {
        this.toAddressSet = toAddressSet;
        this.fromAddressSet = fromAddressSet;
        this.ccAddressSet = ccAddressSet;
        this.bccAddressSet = bccAddressSet;
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
