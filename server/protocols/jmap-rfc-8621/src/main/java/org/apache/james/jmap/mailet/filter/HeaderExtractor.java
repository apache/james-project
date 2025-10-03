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

package org.apache.james.jmap.mailet.filter;

import static org.apache.mailet.base.RFC2822Headers.FROM;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.javax.AddressHelper;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public interface HeaderExtractor extends ThrowingFunction<FilteringHeaders, Stream<String>> {
    Logger LOGGER = LoggerFactory.getLogger(HeaderExtractor.class);

    HeaderExtractor SUBJECT_EXTRACTOR = filteringHeaders ->
        StreamUtils.ofNullables(filteringHeaders.getSubject());
    HeaderExtractor CC_EXTRACTOR = recipientExtractor(Message.RecipientType.CC);
    HeaderExtractor TO_EXTRACTOR = recipientExtractor(Message.RecipientType.TO);
    HeaderExtractor SENT_EXTRACTOR = headers -> StreamUtils.ofNullables(headers.getHeader("Date"));
    HeaderExtractor RECIPIENT_EXTRACTOR = and(TO_EXTRACTOR, CC_EXTRACTOR);
    HeaderExtractor FROM_EXTRACTOR = addressExtractor(filteringHeaders -> filteringHeaders.getHeader(FROM), FROM);
    HeaderExtractor FLAG_EXTRACTOR = FilteringHeaders::getFlags;

    Map<Rule.Condition.Field, HeaderExtractor> HEADER_EXTRACTOR_REGISTRY = ImmutableMap.<Rule.Condition.Field, HeaderExtractor>builder()
        .put(Rule.Condition.FixedField.SUBJECT, SUBJECT_EXTRACTOR)
        .put(Rule.Condition.FixedField.RECIPIENT, RECIPIENT_EXTRACTOR)
        .put(Rule.Condition.FixedField.SENT_DATE, SENT_EXTRACTOR)
        .put(Rule.Condition.FixedField.SAVED_DATE, FilteringHeaders::getSavedDate)
        .put(Rule.Condition.FixedField.INTERNAL_DATE, FilteringHeaders::getInternalDate)
        .put(Rule.Condition.FixedField.FROM, FROM_EXTRACTOR)
        .put(Rule.Condition.FixedField.CC, CC_EXTRACTOR)
        .put(Rule.Condition.FixedField.TO, TO_EXTRACTOR)
        .put(Rule.Condition.FixedField.FLAG, FLAG_EXTRACTOR)
        .build();

    boolean STRICT_PARSING = true;

    static HeaderExtractor and(HeaderExtractor headerExtractor1, HeaderExtractor headerExtractor2) {
        return (FilteringHeaders filteringHeaders) -> StreamUtils.flatten(headerExtractor1.apply(filteringHeaders), headerExtractor2.apply(filteringHeaders));
    }

    static HeaderExtractor recipientExtractor(Message.RecipientType type) {
        String headerName = type.toString();
        ThrowingFunction<FilteringHeaders, String[]> addressGetter = filteringHeaders -> filteringHeaders.getHeader(headerName);

        return addressExtractor(addressGetter, headerName);
    }

    static HeaderExtractor addressExtractor(ThrowingFunction<FilteringHeaders, String[]> addressGetter, String fallbackHeaderName) {
        return filteringHeaders -> {
            try {
                return toAddressContents(addressGetter.apply(filteringHeaders));
            } catch (Exception e) {
                LOGGER.info("Failed parsing header. Falling back to unparsed header value matching", e);
                return Stream.of(filteringHeaders.getHeader(fallbackHeaderName))
                    .map(MimeUtil::unscrambleHeaderValue);
            }
        };
    }

    static Stream<String> toAddressContents(String[] headers) {
        return StreamUtils.ofNullable(headers)
                .map(Throwing.function(string -> InternetAddress.parseHeader(string, !STRICT_PARSING)))
                .flatMap(AddressHelper::asStringStream);
    }

    static Optional<HeaderExtractor> asHeaderExtractor(Rule.Condition.Field field) {
        return Optional.ofNullable(HeaderExtractor.HEADER_EXTRACTOR_REGISTRY.get(field))
            .or(() -> {
                Preconditions.checkArgument(field instanceof Rule.Condition.CustomHeaderField);
                Rule.Condition.CustomHeaderField customHeaderField = (Rule.Condition.CustomHeaderField) field;

                return Optional.of(filteringHeaders -> StreamUtils.ofNullables(filteringHeaders.getHeader(customHeaderField.headerName())));
            });
    }
}
