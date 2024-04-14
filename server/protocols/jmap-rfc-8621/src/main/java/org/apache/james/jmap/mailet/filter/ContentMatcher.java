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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public interface ContentMatcher {

    class AddressHeader {
        private static final Logger LOGGER = LoggerFactory.getLogger(AddressHeader.class);

        private final Optional<String> personal;
        private final Optional<String> address;
        private final String fullAddress;

        private AddressHeader(String fullAddress) {
            this.fullAddress = fullAddress;
            Optional<InternetAddress> internetAddress = parseFullAddress();
            this.address = internetAddress.map(InternetAddress::getAddress);
            this.personal = internetAddress.map(InternetAddress::getPersonal)
                .or(() -> address)
                .or(() -> Optional.of(fullAddress));
        }

        private Optional<InternetAddress> parseFullAddress() {
            try {
                return Optional.of(new InternetAddress(fullAddress));
            } catch (AddressException e) {
                LOGGER.info("error while parsing full address {}", fullAddress, e);
                return Optional.empty();
            }
        }

        boolean matchesIgnoreCase(AddressHeader other) {
            boolean sameAddress = OptionalUtils.matches(address, other.address, String::equalsIgnoreCase);
            boolean samePersonal = OptionalUtils.matches(personal, other.personal, String::equalsIgnoreCase);
            boolean personalMatchesAddress = OptionalUtils.matches(personal, other.address, String::equalsIgnoreCase);
            boolean addressMatchesPersonal = OptionalUtils.matches(address, other.personal, String::equalsIgnoreCase);

            return fullAddress.equalsIgnoreCase(other.fullAddress)
                || (sameAddress && samePersonal)
                || (sameAddress && !personal.isPresent())
                || (samePersonal && !address.isPresent())
                || (personalMatchesAddress && sameAddress)
                || (addressMatchesPersonal && samePersonal);
        }
    }

    class ExactAddressContentMatcher implements ContentMatcher {
        @Override
        public boolean match(Stream<String> contents, String valueToMatch) {
            AddressHeader addressHeaderToMatch =  HeaderExtractor.toAddressContents(new String[] {valueToMatch})
                .map(AddressHeader::new)
                .findAny()
                .orElseGet(() -> new AddressHeader(valueToMatch));

            return contents.map(ContentMatcher::asAddressHeader)
                .anyMatch(addressHeaderToMatch::matchesIgnoreCase);
        }

    }

    ContentMatcher STRING_CONTAINS_MATCHER = (contents, valueToMatch) -> contents.anyMatch(content -> StringUtils.contains(content, valueToMatch));
    ContentMatcher STRING_NOT_CONTAINS_MATCHER = negate(STRING_CONTAINS_MATCHER);
    ContentMatcher STRING_EXACTLY_EQUALS_MATCHER = (contents, valueToMatch) -> contents.anyMatch(content -> StringUtils.equals(content, valueToMatch));
    ContentMatcher STRING_NOT_EXACTLY_EQUALS_MATCHER = negate(STRING_EXACTLY_EQUALS_MATCHER);

    ContentMatcher ADDRESS_CONTAINS_MATCHER = (contents, valueToMatch) -> contents
        .map(ContentMatcher::asAddressHeader)
        .anyMatch(addressHeader -> StringUtils.containsIgnoreCase(addressHeader.fullAddress, valueToMatch));
    ContentMatcher ADDRESS_NOT_CONTAINS_MATCHER = negate(ADDRESS_CONTAINS_MATCHER);
    ContentMatcher ADDRESS_NOT_EXACTLY_EQUALS_MATCHER = negate(new ExactAddressContentMatcher());

    Map<Rule.Condition.Comparator, ContentMatcher> HEADER_ADDRESS_MATCHER_REGISTRY = ImmutableMap.<Rule.Condition.Comparator, ContentMatcher>builder()
        .put(Rule.Condition.Comparator.CONTAINS, ADDRESS_CONTAINS_MATCHER)
        .put(Rule.Condition.Comparator.NOT_CONTAINS, ADDRESS_NOT_CONTAINS_MATCHER)
        .put(Rule.Condition.Comparator.EXACTLY_EQUALS, new ExactAddressContentMatcher())
        .put(Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, ADDRESS_NOT_EXACTLY_EQUALS_MATCHER)
        .build();

    Map<Rule.Condition.Comparator, ContentMatcher> CONTENT_STRING_MATCHER_REGISTRY = ImmutableMap.<Rule.Condition.Comparator, ContentMatcher>builder()
        .put(Rule.Condition.Comparator.CONTAINS, STRING_CONTAINS_MATCHER)
        .put(Rule.Condition.Comparator.NOT_CONTAINS, STRING_NOT_CONTAINS_MATCHER)
        .put(Rule.Condition.Comparator.EXACTLY_EQUALS, STRING_EXACTLY_EQUALS_MATCHER)
        .put(Rule.Condition.Comparator.NOT_EXACTLY_EQUALS, STRING_NOT_EXACTLY_EQUALS_MATCHER)
        .build();

    Map<Rule.Condition.Field, Map<Rule.Condition.Comparator, ContentMatcher>> CONTENT_MATCHER_REGISTRY = ImmutableMap.<Rule.Condition.Field, Map<Rule.Condition.Comparator, ContentMatcher>>builder()
        .put(Rule.Condition.Field.SUBJECT, CONTENT_STRING_MATCHER_REGISTRY)
        .put(Rule.Condition.Field.TO, HEADER_ADDRESS_MATCHER_REGISTRY)
        .put(Rule.Condition.Field.CC, HEADER_ADDRESS_MATCHER_REGISTRY)
        .put(Rule.Condition.Field.RECIPIENT, HEADER_ADDRESS_MATCHER_REGISTRY)
        .put(Rule.Condition.Field.FROM, HEADER_ADDRESS_MATCHER_REGISTRY)
        .build();

    static ContentMatcher negate(ContentMatcher contentMatcher) {
        return (Stream<String> contents, String valueToMatch) ->
            !contentMatcher.match(contents, valueToMatch);
    }

    static Optional<ContentMatcher> asContentMatcher(Rule.Condition.Field field, Rule.Condition.Comparator comparator) {
        return Optional
            .ofNullable(CONTENT_MATCHER_REGISTRY.get(field))
            .map(matcherRegistry -> matcherRegistry.get(comparator));
    }

    static AddressHeader asAddressHeader(String addressAsString) {
        return new AddressHeader(addressAsString);
    }

    boolean match(Stream<String> contents, String valueToMatch);

}
