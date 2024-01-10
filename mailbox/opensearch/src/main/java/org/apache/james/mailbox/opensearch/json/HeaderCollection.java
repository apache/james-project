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

package org.apache.james.mailbox.opensearch.json;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mailbox.store.search.comparator.SentDateComparator;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.MimeUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class HeaderCollection {

    public static class Header {
        private final String headerName;
        private final String value;

        Header(String headerName, String value) {
            this.headerName = headerName;
            this.value = value;
        }

        @JsonProperty(JsonMessageConstants.HEADER.NAME)
        public String getHeaderName() {
            return headerName;
        }

        @JsonProperty(JsonMessageConstants.HEADER.VALUE)
        public String getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Header) {
                Header header = (Header) o;

                return Objects.equals(this.headerName, header.headerName)
                    && Objects.equals(this.value, header.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(headerName, value);
        }
    }

    public static class Builder {

        private final ImmutableSet.Builder<EMailer> toAddressSet;
        private final ImmutableSet.Builder<EMailer> fromAddressSet;
        private final ImmutableSet.Builder<EMailer> ccAddressSet;
        private final ImmutableSet.Builder<EMailer> bccAddressSet;
        private final ImmutableSet.Builder<EMailer> replyToAddressSet;
        private final ImmutableSet.Builder<String> subjectSet;
        private final ImmutableList.Builder<Header> headers;
        private Optional<ZonedDateTime> sentDate;
        private Optional<String> messageID;

        private Builder() {
            toAddressSet = ImmutableSet.builder();
            fromAddressSet = ImmutableSet.builder();
            ccAddressSet = ImmutableSet.builder();
            bccAddressSet = ImmutableSet.builder();
            replyToAddressSet = ImmutableSet.builder();
            subjectSet = ImmutableSet.builder();
            headers = ImmutableList.builder();
            sentDate = Optional.empty();
            messageID = Optional.empty();
        }

        public Builder add(Field field) {
            Preconditions.checkNotNull(field);
            String headerName = field.getName().toLowerCase(Locale.US);
            String rawHeaderValue = field.getBody();
            String sanitizedValue = MimeUtil.unscrambleHeaderValue(rawHeaderValue);

            headers.add(new Header(headerName, sanitizedValue));

            handleSpecificHeader(headerName, sanitizedValue, rawHeaderValue);
            return this;
        }

        public HeaderCollection build() {
            return new HeaderCollection(
                toAddressSet.build(),
                fromAddressSet.build(),
                ccAddressSet.build(),
                bccAddressSet.build(),
                replyToAddressSet.build(),
                subjectSet.build(),
                headers.build(),
                sentDate,
                messageID);
        }

        private void handleSpecificHeader(String headerName, String headerValue, String rawHeaderValue) {
            switch (headerName) {
                case TO:
                case FROM:
                case CC:
                case BCC:
                case REPLY_TO:
                    manageAddressField(headerName, rawHeaderValue);
                    break;
                case SUBJECT:
                    subjectSet.add(SearchUtil.getBaseSubject(headerValue));
                    break;
                case DATE:
                    sentDate = SentDateComparator.toISODate(headerValue);
                    break;
                case MESSAGE_ID:
                    messageID = Optional.ofNullable(headerValue);
                    break;
            }
        }

        private void manageAddressField(String headerName, String rawHeaderValue) {
            ImmutableSet.Builder<EMailer> addressSet = getAddressSet(headerName);
            LenientAddressParser.DEFAULT
                .parseAddressList(rawHeaderValue)
                .flatten()
                .stream()
                .map(mailbox -> new EMailer(Optional.ofNullable(mailbox.getName()), mailbox.getAddress(), mailbox.getDomain()))
                .forEach(addressSet::add);
        }

        private ImmutableSet.Builder<EMailer> getAddressSet(String headerName) {
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
    public static final String MESSAGE_ID = "message-id";

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableSet<EMailer> toAddressSet;
    private final ImmutableSet<EMailer> fromAddressSet;
    private final ImmutableSet<EMailer> ccAddressSet;
    private final ImmutableSet<EMailer> bccAddressSet;
    private final ImmutableSet<EMailer> replyToAddressSet;
    private final ImmutableSet<String> subjectSet;
    private final List<Header> headers;
    private final Optional<ZonedDateTime> sentDate;
    private final Optional<String> messageID;

    private HeaderCollection(ImmutableSet<EMailer> toAddressSet, ImmutableSet<EMailer> fromAddressSet,
                             ImmutableSet<EMailer> ccAddressSet, ImmutableSet<EMailer> bccAddressSet,
                             ImmutableSet<EMailer> replyToAddressSet, ImmutableSet<String> subjectSet,
                             List<Header> headers, Optional<ZonedDateTime> sentDate, Optional<String> messageID) {
        this.toAddressSet = toAddressSet;
        this.fromAddressSet = fromAddressSet;
        this.ccAddressSet = ccAddressSet;
        this.bccAddressSet = bccAddressSet;
        this.replyToAddressSet = replyToAddressSet;
        this.subjectSet = subjectSet;
        this.headers = headers;
        this.sentDate = sentDate;
        this.messageID = messageID;
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

    public List<Header> getHeaders() {
        return headers;
    }

    public Optional<String> getMessageID() {
        return messageID;
    }
}
