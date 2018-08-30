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

import static org.apache.james.core.builder.MimeMessageBuilder.mimeMessageBuilder;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.EXACTLY_EQUALS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.NOT_EXACTLY_EQUALS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.CC;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.FROM;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.RECIPIENT;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.SUBJECT;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.TO;
import static org.apache.james.jmap.mailet.filter.ActionApplier.DELIVERY_PATH_PREFIX;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.BOU;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.CC_HEADER;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.EMPTY;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULLNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULL_SCRAMBLED_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.GA_BOU_ZO_MEU_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_MAILBOX_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SHOULD_NOT_MATCH;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.TO_HEADER;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNFOLDED_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.UNSCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_AND_UNFOLDED_USER_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_2_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_3_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_4_FULL_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.User;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.mailet.filter.JMAPFilteringExtension.JMAPFilteringTestSystem;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@ExtendWith(JMAPFilteringExtension.class)
class JMAPFilteringTest {

    static class FilteringArgumentBuilder {
        private Optional<String> description;
        private Optional<Rule.Condition.Field> field;
        private MimeMessageBuilder mimeMessageBuilder;
        private Optional<String> valueToMatch;
        
        private FilteringArgumentBuilder() {
            this.description = Optional.empty();
            this.field = Optional.empty();
            mimeMessageBuilder = MimeMessageBuilder.mimeMessageBuilder();
            this.valueToMatch = Optional.empty();
        }

        public FilteringArgumentBuilder description(String description) {
            this.description = Optional.ofNullable(description);
            return this;
        }

        public FilteringArgumentBuilder field(Rule.Condition.Field field) {
            this.field = Optional.ofNullable(field);
            return this;
        }

        public FilteringArgumentBuilder from(String from) {
            Optional.ofNullable(from).ifPresent(Throwing.consumer(mimeMessageBuilder::addFrom));
            return this;
        }

        public FilteringArgumentBuilder noHeader() {
            return this;
        }

        public FilteringArgumentBuilder toRecipient(String toRecipient) {
            Optional.ofNullable(toRecipient).ifPresent(Throwing.consumer(mimeMessageBuilder::addToRecipient));
            return this;
        }

        public FilteringArgumentBuilder ccRecipient(String ccRecipient) {
            Optional.ofNullable(ccRecipient).ifPresent(Throwing.consumer(mimeMessageBuilder::addCcRecipient));
            return this;
        }

        public FilteringArgumentBuilder bccRecipient(String bccRecipient) {
            Optional.ofNullable(bccRecipient).ifPresent(Throwing.consumer(mimeMessageBuilder::addBccRecipient));
            return this;
        }

        public FilteringArgumentBuilder header(String headerName, String headerValue) {
            mimeMessageBuilder.addHeader(headerName, headerValue);
            return this;
        }

        public FilteringArgumentBuilder headerForField(String headerValue) {
            Preconditions.checkState(field.isPresent(), "field should be set first");

            mimeMessageBuilder.addHeader(field.get().asString(), headerValue);
            return this;
        }

        public FilteringArgumentBuilder subject(String subject) {
            mimeMessageBuilder.setSubject(subject);
            return this;
        }

        public FilteringArgumentBuilder valueToMatch(String valueToMatch) {
            this.valueToMatch = Optional.ofNullable(valueToMatch);
            return this;
        }

        public FilteringArgumentBuilder scrambledSubjectToMatch(String valueToMatch) {
            return description("normal content")
                .field(SUBJECT)
                .subject(SCRAMBLED_SUBJECT)
                .valueToMatch(valueToMatch);
        }

        public FilteringArgumentBuilder scrambledSubjectShouldNotMatchCaseSensitive() {
            return description("normal content (case sensitive)")
                .field(SUBJECT)
                .subject(SCRAMBLED_SUBJECT)
                .valueToMatch(SCRAMBLED_SUBJECT.toUpperCase(Locale.FRENCH));
        }

        public FilteringArgumentBuilder unscrambledSubjectToMatch(String valueToMatch) {
            return description("unscrambled content")
                .field(SUBJECT)
                .subject(UNSCRAMBLED_SUBJECT)
                .valueToMatch(valueToMatch);
        }

        public FilteringArgumentBuilder unscrambledSubjectShouldNotMatchCaseSensitive() {
            return description("unscrambled content (case sensitive)")
                    .field(SUBJECT)
                    .subject(UNSCRAMBLED_SUBJECT)
                    .valueToMatch(UNSCRAMBLED_SUBJECT.toUpperCase(Locale.FRENCH));
        }

        public Arguments build() {
            Preconditions.checkState(description.isPresent());
            Preconditions.checkState(field.isPresent());
            Preconditions.checkState(valueToMatch.isPresent());
            
            return Arguments.of(description.get(), field.get(), mimeMessageBuilder, valueToMatch.get());
        }

    }

    static FilteringArgumentBuilder argumentBuilder() {
        return new FilteringArgumentBuilder();
    }

    static FilteringArgumentBuilder argumentBuilder(Rule.Condition.Field field) {
        return new FilteringArgumentBuilder()
            .field(field);
    }

    static Stream<Arguments> exactlyEqualsTestSuite() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> Stream.of(
                    argumentBuilder(headerField)
                        .description("full address value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME),
                    argumentBuilder(headerField)
                        .description("full address value (different case)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME.toUpperCase(Locale.ENGLISH)),
                    argumentBuilder(headerField)
                        .description("address only value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS),
                    argumentBuilder(headerField)
                        .description("address only value (different case)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS.toUpperCase(Locale.ENGLISH)),
                    argumentBuilder(headerField)
                        .description("personal only value")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_FULL_ADDRESS),
                    argumentBuilder(headerField)
                        .description("personal only value (different case)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_FULL_ADDRESS.toUpperCase()),
                    argumentBuilder(headerField)
                        .description("personal header should match personal")
                        .headerForField(USER_1_USERNAME)
                        .valueToMatch(USER_1_USERNAME),
                    argumentBuilder(headerField)
                        .description("address header should match address")
                        .headerForField(USER_1_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS),
                    argumentBuilder(headerField)
                        .description("multiple headers")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .headerForField(USER_2_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME),
                    argumentBuilder(headerField)
                        .description("scrambled content")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(FRED_MARTIN_FULLNAME),
                    argumentBuilder(headerField)
                        .description("folded content")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME),
                    argumentBuilder(headerField)
                        .description("folded content (different case)")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME.toUpperCase()),
                    argumentBuilder(headerField)
                        .description("invalid address, personal match")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("Benoit"),
                    argumentBuilder(headerField)
                        .description("invalid address, address match")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("invalid"),
                    argumentBuilder(headerField)
                        .description("invalid address, full match")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("Benoit <invalid>"),
                    argumentBuilder(headerField)
                        .description("invalid header, full match")
                        .headerForField("Benoit <invalid")
                        .valueToMatch("Benoit <invalid")
                    ).map(FilteringArgumentBuilder::build)),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder(RECIPIENT)
                        .description("full address " + headerName + " header")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_FULL_ADDRESS),
                    argumentBuilder(RECIPIENT)
                        .description("full address " + headerName + " header (different case)")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_FULL_ADDRESS.toUpperCase(Locale.ENGLISH)),
                    argumentBuilder(RECIPIENT)
                        .description("address only " + headerName + " header")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_ADDRESS),
                    argumentBuilder(RECIPIENT)
                        .description("personal only " + headerName + " header")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(USER_3_USERNAME),
                    argumentBuilder(RECIPIENT)
                        .description("scrambled content in " + headerName + " header")
                        .header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(FRED_MARTIN_FULLNAME),
                    argumentBuilder(RECIPIENT)
                        .description("folded content in " + headerName + " header")
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME),
                    argumentBuilder(RECIPIENT)
                        .description("invalid " + headerName + " address, personal match")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("Benoit"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid " + headerName + " address, address match")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("invalid"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid " + headerName + " address, full match")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("Benoit <invalid>"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid " + headerName + ", full match")
                        .header(headerName, "Benoit <invalid")
                        .valueToMatch("Benoit <invalid"))
                    .map(FilteringArgumentBuilder::build)),
            Stream.of(
                argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch(USER_4_FULL_ADDRESS)
                    .build(),
                argumentBuilder().scrambledSubjectToMatch(UNSCRAMBLED_SUBJECT).build(),
                argumentBuilder().unscrambledSubjectToMatch(UNSCRAMBLED_SUBJECT).build()));
    }

    static Stream<Arguments> containsTestSuite() {
        return Stream.concat(
            exactlyEqualsTestSuite(),
            containsArguments());
    }

    private static Stream<Arguments> containsArguments() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> Stream.of(
                    argumentBuilder(headerField)
                        .description("full address value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1 <"),
                    argumentBuilder(headerField)
                        .description("full address value (partial matching, different case)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("SER1 <"),
                    argumentBuilder(headerField)
                        .description("address only value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"),
                    argumentBuilder(headerField)
                        .description("personal only value (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1"),
                    argumentBuilder(headerField)
                        .description("address header & match in the address (partial matching)")
                        .headerForField(USER_1_ADDRESS)
                        .valueToMatch("ser1@jam"),
                    argumentBuilder(headerField)
                        .description("raw value matching (partial matching)")
                        .headerForField(GA_BOU_ZO_MEU_FULL_ADDRESS)
                        .valueToMatch(BOU),
                    argumentBuilder(headerField)
                        .description("multiple headers (partial matching)")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .headerForField(USER_2_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"),
                    argumentBuilder(headerField)
                        .description("scrambled content (partial matching)")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch("déric MAR"),
                    argumentBuilder(headerField)
                        .description("folded content (partial matching)")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch("ded_us"),
                    argumentBuilder(headerField)
                        .description("invalid address, personal match (partial matching)")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("enoi"),
                    argumentBuilder(headerField)
                        .description("invalid address, address match (partial matching)")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("nvali"),
                    argumentBuilder(headerField)
                        .description("invalid address, full match (partial matching)")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch("enoit <invali"),
                    argumentBuilder(headerField)
                        .description("invalid header, full match (partial matching)")
                        .headerForField("Benoit <invalid")
                        .valueToMatch("enoit <invali"),
                    argumentBuilder(headerField)
                        .description("invalid header, personal match (partial matching)")
                        .headerForField("Benoit <invalid")
                        .valueToMatch("enoi"),
                    argumentBuilder(headerField)
                        .description("invalid header, address match (partial matching)")
                        .headerForField("Benoit <invalid")
                        .valueToMatch("nvali"))
                    .map(FilteringArgumentBuilder::build)),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder(RECIPIENT)
                        .description("full address " + headerName + " header (partial matching)")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3 <us"),
                    argumentBuilder(RECIPIENT)
                        .description("full address " + headerName + " header (partial matching, different case)")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("SER3 <US"),
                    argumentBuilder(RECIPIENT)
                        .description("address only " + headerName + " header (partial matching)")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3@jam"),
                    argumentBuilder(RECIPIENT)
                        .description("personal only " + headerName + " header (partial matching)")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch("ser3"),
                    argumentBuilder(RECIPIENT)
                        .description("scrambled content in " + headerName + " header (partial matching)")
                        .header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch("déric MAR"),
                    argumentBuilder(RECIPIENT)
                        .description("folded content in " + headerName + " header (partial matching)")
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch("folded_us"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid address, personal match (partial matching)")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("enoi"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid address, address match (partial matching)")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("nvali"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid address, full match (partial matching)")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch("enoit <invali"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid header, full match (partial matching)")
                        .header(headerName, "Benoit <invalid")
                        .valueToMatch("enoit <invali"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid header, personal match (partial matching)")
                        .header(headerName, "Benoit <invalid")
                        .valueToMatch("enoi"),
                    argumentBuilder(RECIPIENT)
                        .description("invalid header, address match (partial matching)")
                        .header(headerName, "Benoit <invalid")
                        .valueToMatch("nvali"))
                    .map(FilteringArgumentBuilder::build)),
            Stream.of(
                argumentBuilder().description("multiple to and cc headers (partial matching)").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch("user4@jam").build(),
                argumentBuilder().scrambledSubjectToMatch("is the subject").build(),
                argumentBuilder().unscrambledSubjectToMatch("rédéric MART").build()));
    }

    static Stream<Arguments> notEqualsTestSuite() {
        return Stream.concat(
            notContainsTestSuite(),
            containsArguments());
    }

    static Stream<Arguments> notContainsTestSuite() {
        return StreamUtils.flatten(
            Stream.of(FROM, TO, CC)
                .flatMap(headerField -> Stream.of(
                    argumentBuilder(headerField)
                        .description("normal content")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("multiple headers")
                        .headerForField(USER_1_FULL_ADDRESS)
                        .from(USER_2_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("scrambled content")
                        .headerForField(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("folded content")
                        .headerForField(USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("empty content")
                        .headerForField(EMPTY)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("invalid address, personal match")
                        .headerForField("Benoit <invalid>")
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(headerField)
                        .description("invalid header, full match")
                        .headerForField("Benoit <invalid")
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .map(FilteringArgumentBuilder::build)),
            Stream.of(TO_HEADER, CC_HEADER)
                .flatMap(headerName -> Stream.of(
                    argumentBuilder(RECIPIENT)
                        .description("normal content " + headerName + " header")
                        .header(headerName, USER_3_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(RECIPIENT)
                        .description("scrambled content in " + headerName + " header")
                        .field(RECIPIENT).header(headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(RECIPIENT)
                        .description("folded content in " + headerName + " header")
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(RECIPIENT)
                        .description("bcc header")
                        .header(headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(RECIPIENT)
                        .description("invalid address, personal match")
                        .header(headerName, "Benoit <invalid>")
                        .valueToMatch(SHOULD_NOT_MATCH),
                    argumentBuilder(RECIPIENT)
                        .description("invalid header, full match")
                        .header(headerName, "Benoit <invalid")
                        .valueToMatch(SHOULD_NOT_MATCH))
                    .map(FilteringArgumentBuilder::build)),
            Stream.of(
                argumentBuilder().description("multiple to and cc headers").field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch(SHOULD_NOT_MATCH)
                    .build(),
                argumentBuilder().description("matching bcc headers").field(RECIPIENT)
                    .bccRecipient(USER_1_FULL_ADDRESS)
                    .valueToMatch(USER_1_FULL_ADDRESS)
                    .build(),
                argumentBuilder().scrambledSubjectToMatch(SHOULD_NOT_MATCH).build(),
                argumentBuilder().scrambledSubjectShouldNotMatchCaseSensitive().build(),
                argumentBuilder().unscrambledSubjectToMatch(SHOULD_NOT_MATCH).build(),
                argumentBuilder().unscrambledSubjectShouldNotMatchCaseSensitive().build()),
            Stream.of(Rule.Condition.Field.values())
                .map(field -> argumentBuilder()
                    .description("no header")
                    .field(field)
                    .noHeader()
                    .valueToMatch(USER_1_USERNAME)
                    .build()));
    }

    @ParameterizedTest(name = "CONTAINS should match for header field {1}, with {0}")
    @MethodSource("containsTestSuite")
    void matchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "CONTAINS should not match for header field {1}, with {0}")
    @MethodSource("notContainsTestSuite")
    void notMatchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
    }

    @ParameterizedTest(name = "NOT-CONTAINS should be matching for field {1}, with {0}")
    @MethodSource("notContainsTestSuite")
    void matchingNotContainsTest(String testDescription,
                                 Rule.Condition.Field fieldToMatch,
                                 MimeMessageBuilder mimeMessageBuilder,
                                 String valueToMatch,
                                 JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }


    @ParameterizedTest(name = "NOT-CONTAINS should not be matching for field {1}, with {0}")
    @MethodSource("containsTestSuite")
    void notContainsNotMatchingTest(String testDescription,
                                    Rule.Condition.Field fieldToMatch,
                                    MimeMessageBuilder mimeMessageBuilder,
                                    String valueToMatch,
                                    JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should match for header field {1}, with {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void equalsMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should not match for header field {1}, with {0}")
    @MethodSource("notEqualsTestSuite")
    void equalsNotMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should match for header field {1}, with {0}")
    @MethodSource("notEqualsTestSuite")
    void notEqualsMatchingTest(String testDescription,
                               Rule.Condition.Field fieldToMatch,
                               MimeMessageBuilder mimeMessageBuilder,
                               String valueToMatch,
                               JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1);
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should not match for header field {1}, with {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void notMatchingNotEqualsTests(String testDescription,
                                   Rule.Condition.Field fieldToMatch,
                                   MimeMessageBuilder mimeMessageBuilder,
                                   String valueToMatch,
                                   JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
            .isNull();
    }

    @Nested
    class MultiRuleBehaviourTest {
        @Test
        void mailDirectiveShouldSetFirstMatchedRuleWhenMultipleRules(JMAPFilteringTestSystem testSystem) throws Exception {
            InMemoryMailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxId mailbox1Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .condition(Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox1Id.serialize())))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("2"))
                    .name("rule 2")
                    .condition(Rule.Condition.of(FROM, NOT_CONTAINS, USER_1_USERNAME))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox2Id.serialize())))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("3"))
                    .name("rule 3")
                    .condition(Rule.Condition.of(TO, EXACTLY_EQUALS, USER_3_ADDRESS))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox3Id.serialize())))
                    .build());

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .addFrom(USER_2_ADDRESS)
                    .addToRecipient(USER_3_ADDRESS)
                    .setSubject(UNSCRAMBLED_SUBJECT));

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo("RECIPIENT_1_MAILBOX_1");
        }

        @Test
        void mailDirectiveShouldSetFirstMatchedMailboxWhenMultipleMailboxes(JMAPFilteringTestSystem testSystem) throws Exception {
            InMemoryMailboxManager mailboxManager = testSystem.getMailboxManager();
            MailboxId mailbox1Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(mailboxManager, RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            testSystem.getFilteringManagement().defineRulesForUser(User.fromUsername(RECIPIENT_1_USERNAME),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .condition(Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of(
                        mailbox3Id.serialize(),
                        mailbox2Id.serialize(),
                        mailbox1Id.serialize()))))
                    .build());

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .setSubject(UNSCRAMBLED_SUBJECT));

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isEqualTo("RECIPIENT_1_MAILBOX_3");
        }

        @Test
        void mailDirectiveShouldNotBeSetWhenAllDoNotExactlyEqualsRuleValue(JMAPFilteringTestSystem testSystem) throws Exception {
            testSystem.defineRulesForRecipient1(
                Rule.Condition.of(FROM, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(FROM, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(TO, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(TO, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(CC, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(CC, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(RECIPIENT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(RECIPIENT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(SUBJECT, CONTAINS, USER_1_FULL_ADDRESS),
                Rule.Condition.of(SUBJECT, EXACTLY_EQUALS, USER_1_FULL_ADDRESS));

            FakeMail mail = testSystem.asMail(mimeMessageBuilder());
            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
        }

        @Test
        void mailDirectiveShouldNotBeSetWhenNoneRulesValueIsContained(JMAPFilteringTestSystem testSystem) throws Exception {
            testSystem.defineRulesForRecipient1(
                Rule.Condition.of(FROM, CONTAINS, SHOULD_NOT_MATCH),
                Rule.Condition.of(TO, CONTAINS, SHOULD_NOT_MATCH),
                Rule.Condition.of(CC, CONTAINS, SHOULD_NOT_MATCH));

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .addFrom(USER_1_FULL_ADDRESS)
                    .addToRecipient(USER_2_FULL_ADDRESS)
                    .addCcRecipient(USER_3_FULL_ADDRESS));

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(DELIVERY_PATH_PREFIX + RECIPIENT_1_USERNAME))
                .isNull();
        }
    }
}