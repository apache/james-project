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
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.BOU;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.EMPTY;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULLNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.FRED_MARTIN_FULL_SCRAMBLED_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.GA_BOU_ZO_MEU_FULL_ADDRESS;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_MAILBOX_1;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.RECIPIENT_1_USERNAME;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SCRAMBLED_SUBJECT;
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.SHOULD_NOT_MATCH;
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
import static org.apache.james.jmap.mailet.filter.JMAPFilteringFixture.USER_4_FULL_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rule.Condition.Field;
import org.apache.james.jmap.mailet.filter.JMAPFilteringExtension.JMAPFilteringTestSystem;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.StorageDirective;
import org.apache.mailet.base.RFC2822Headers;
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
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

@ExtendWith(JMAPFilteringExtension.class)
class JMAPFilteringTest {

    private static final AttributeName RECIPIENT_1_USERNAME_ATTRIBUTE_NAME = AttributeName.of("DeliveryPaths_" + RECIPIENT_1_USERNAME.asString());
    private static final Attribute RECIPIENT_1_MAILBOX_1_ATTRIBUTE = new Attribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME, AttributeValue.of(ImmutableList.of(RECIPIENT_1_MAILBOX_1)));

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

        public FilteringArgumentBuilder testForUpperCase() {
            return description(description.get() + " (different case)")
                .valueToMatch(valueToMatch.get().toUpperCase(Locale.US));
        }

        public Arguments build() {
            Preconditions.checkState(description.isPresent());
            Preconditions.checkState(field.isPresent());
            Preconditions.checkState(valueToMatch.isPresent());
            
            return Arguments.of(description.get(), field.get(), mimeMessageBuilder, valueToMatch.get());
        }
    }

    static Stream<Arguments> forBothCase(FilteringArgumentBuilder builder) {
        return Stream.of(
            builder.build(),
            builder.testForUpperCase().build());
    }

    static FilteringArgumentBuilder argumentBuilder() {
        return new FilteringArgumentBuilder();
    }

    static FilteringArgumentBuilder argumentBuilder(Rule.Condition.Field field) {
        return new FilteringArgumentBuilder()
            .field(field);
    }

    static class FieldAndHeader {
        private final Rule.Condition.Field field;
        private final String headerName;

        public FieldAndHeader(Rule.Condition.Field field, String headerName) {
            this.field = field;
            this.headerName = headerName;
        }
    }

    @FunctionalInterface
    interface AttributeEquals {
        void isEqualTo(Attribute other);
    }

    public static AttributeEquals assertThatAttribute(Attribute attribute) {
        return other -> {
            assertThat(attribute.getName()).isEqualTo(other.getName());
            assertThat(unbox(attribute)).isEqualTo(unbox(other));
        };
    }

    public static AttributeEquals assertThatAttribute(Optional<Attribute> attribute) {
        return assertThatAttribute(attribute.get());
    }

    static Pair<AttributeName, String> unbox(Attribute attribute) {
        Collection<AttributeValue> collection = (Collection<AttributeValue>) attribute.getValue().getValue();
        return Pair.of(attribute.getName(), (String) collection.stream().findFirst().get().getValue());
    }

    public static final ImmutableList<FieldAndHeader> ADDRESS_TESTING_COMBINATION = ImmutableList.of(
        new FieldAndHeader(Field.FROM, RFC2822Headers.FROM),
        new FieldAndHeader(Field.TO, RFC2822Headers.TO),
        new FieldAndHeader(Field.CC, RFC2822Headers.CC),
        new FieldAndHeader(Field.RECIPIENT, RFC2822Headers.TO),
        new FieldAndHeader(Field.RECIPIENT, RFC2822Headers.CC));

    static Stream<Arguments> exactlyEqualsTestSuite() {
        return StreamUtils.flatten(
            ADDRESS_TESTING_COMBINATION
                .stream()
                .flatMap(fieldAndHeader -> Stream.of(
                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a full " + fieldAndHeader.headerName + " header with multiple addresses")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS + ", " + USER_2_FULL_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a failing " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "invalid@ white.space.in.domain.tld")
                        .valueToMatch("invalid@ white.space.in.domain.tld"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a coma quoted " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("\"a, b\"@quoted.com"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a coma quoted " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("Toto"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full exact match in a coma quoted " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("Toto <\"a, b\"@quoted.com>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a failing + coma quoted" + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "invalid@ space.org, Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("\"a, b\"@quoted.com"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a failing + coma quoted " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "invalid@ space.org, Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("Toto"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full exact match in a failing + coma quoted " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, "invalid@ space.org, Toto <\"a, b\"@quoted.com>")
                        .valueToMatch("Toto <\"a, b\"@quoted.com>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a failing " + fieldAndHeader.headerName + " header with multiple values")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS + ", invalid@ white.space.in.domain.tld")
                        .valueToMatch(USER_1_FULL_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a failing " + fieldAndHeader.headerName + " header with multiple values")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS + ", invalid@ white.space.in.domain.tld")
                        .valueToMatch(USER_1_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a failing " + fieldAndHeader.headerName + " header with multiple values")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS + ", invalid@ white.space.in.domain.tld")
                        .valueToMatch(USER_1_USERNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header exact match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch(USER_1_FULL_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Exact match in a full " + fieldAndHeader.headerName + " header with a symetric emailer")
                        .header(fieldAndHeader.headerName, "\"toto@domain.tld\" <toto@domain.tld>")
                        .valueToMatch("toto@domain.tld"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a username only " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_USERNAME)
                        .valueToMatch(USER_1_USERNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in an address only " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_ADDRESS)
                        .valueToMatch(USER_1_ADDRESS),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username matching in multiple " + fieldAndHeader.headerName + " headers")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .header(fieldAndHeader.headerName, USER_2_FULL_ADDRESS)
                        .valueToMatch(USER_1_USERNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a scrambled full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(FRED_MARTIN_FULLNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a folded full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(UNFOLDED_USERNAME),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username exact match in a full " + fieldAndHeader.headerName + " with an invalid address")
                        .header(fieldAndHeader.headerName, "Benoit <invalid>")
                        .valueToMatch("Benoit"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a full " + fieldAndHeader.headerName + " with an invalid address")
                        .header(fieldAndHeader.headerName, "Benoit <invalid>")
                        .valueToMatch("invalid"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header exact match in a full " + fieldAndHeader.headerName + " with an invalid address")
                        .header(fieldAndHeader.headerName, "Benoit <invalid>")
                        .valueToMatch("Benoit <invalid>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header exact match in a full " + fieldAndHeader.headerName + " with an invalid structure")
                        .header(fieldAndHeader.headerName, "Benoit <invalid")
                        .valueToMatch("Benoit <invalid"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header exact match in a full " + fieldAndHeader.headerName + " with an invalid structure - multi address")
                        .header(fieldAndHeader.headerName, "Valid <toto@domain.tld>, Benoit <invalid")
                        .valueToMatch("Benoit <invalid"))
                    .flatMap(JMAPFilteringTest::forBothCase)),

            Stream.of(
                argumentBuilder().description("Full header match with multiple to and cc headers")
                    .field(RECIPIENT)
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
            ADDRESS_TESTING_COMBINATION.stream()
                .flatMap(fieldAndHeader -> Stream.of(
                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header partial match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1 <"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address exact match in a full " + fieldAndHeader.headerName + " header with multiple addresses")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS + ", Invalid <invalid@ white.space.in.domain.tld>")
                        .valueToMatch("invalid@ white.space.in.domain.tld"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address partial match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a full " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName,USER_1_FULL_ADDRESS)
                        .valueToMatch("ser1"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address partial match in an address only " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName,USER_1_ADDRESS)
                        .valueToMatch("ser1@jam"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a headername only " + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName,GA_BOU_ZO_MEU_FULL_ADDRESS)
                        .valueToMatch(BOU),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address partial match against multiple" + fieldAndHeader.headerName + " header")
                        .header(fieldAndHeader.headerName,USER_1_FULL_ADDRESS)
                        .header(fieldAndHeader.headerName,USER_2_FULL_ADDRESS)
                        .valueToMatch("ser1@jam"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a scrambled " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName,FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch("déric MAR"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a folded " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName,USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch("ded_us"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a " + fieldAndHeader.headerName + " full header with invalid address")
                        .header(fieldAndHeader.headerName,"Benoit <invalid>")
                        .valueToMatch("enoi"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address partial match in a " + fieldAndHeader.headerName + " full header with invalid address")
                        .header(fieldAndHeader.headerName,"Benoit <invalid>")
                        .valueToMatch("nvali"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header partial match in a " + fieldAndHeader.headerName + " full header with invalid address")
                        .header(fieldAndHeader.headerName,"Benoit <invalid>")
                        .valueToMatch("enoit <invali"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Full header partial match in a " + fieldAndHeader.headerName + " full header with invalid structure")
                        .header(fieldAndHeader.headerName,"Benoit <invalid")
                        .valueToMatch("enoit <invali"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Username partial match in a " + fieldAndHeader.headerName + " full header with invalid structure")
                        .header(fieldAndHeader.headerName,"Benoit <invalid")
                        .valueToMatch("enoi"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Address partial match in a " + fieldAndHeader.headerName + " full header with invalid structure")
                        .header(fieldAndHeader.headerName,"Benoit <invalid")
                        .valueToMatch("nvali"))

                    .flatMap(JMAPFilteringTest::forBothCase)),
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
            ADDRESS_TESTING_COMBINATION.stream()
                .flatMap(fieldAndHeader -> Stream.of(
                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in a " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in multiple " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName, USER_1_FULL_ADDRESS)
                        .header(fieldAndHeader.headerName, USER_2_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in a scrambled " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName, FRED_MARTIN_FULL_SCRAMBLED_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in a folded " + fieldAndHeader.headerName + " full header")
                        .header(fieldAndHeader.headerName, USER_1_AND_UNFOLDED_USER_FULL_ADDRESS)
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in a " + fieldAndHeader.headerName + " empty header")
                        .header(fieldAndHeader.headerName, EMPTY)
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch when different address in a fully specified emailer for " + fieldAndHeader.headerName + " field")
                        .header(fieldAndHeader.headerName, "\"me\" <notme@example.com>")
                        .valueToMatch("\"me\" <me@example.com>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch when different username in a fully specified emailer for " + fieldAndHeader.headerName + " field")
                        .header(fieldAndHeader.headerName, "\"notme\" <me@example.com>")
                        .valueToMatch("\"definitlyme\" <me@example.com>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("No match in a full " + fieldAndHeader.headerName + " header with a symetric emailer - different personal")
                        .header(fieldAndHeader.headerName, "\"toto@domain.tld\" <toto@domain.tld>")
                        .valueToMatch("\"tata@domain.tld\" <toto@domain.tld>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("No match in a full " + fieldAndHeader.headerName + " header with a symetric emailer - different address")
                        .header(fieldAndHeader.headerName, "\"toto@domain.tld\" <toto@domain.tld>")
                        .valueToMatch("\"toto@domain.tld\" <tata@domain.tld>"),

                    argumentBuilder(fieldAndHeader.field)
                        .description("Nomatch in a missing " + fieldAndHeader.headerName + " header")
                        .valueToMatch(SHOULD_NOT_MATCH),

                    argumentBuilder(fieldAndHeader.field)
                        .description("No username match in a " + fieldAndHeader.headerName + " full header with invalid structure")
                        .header(fieldAndHeader.headerName, "Benoit <invalid>")
                        .valueToMatch(SHOULD_NOT_MATCH))

                    .map(FilteringArgumentBuilder::build)),
            Stream.of(
                argumentBuilder().description("multiple to and cc headers")
                    .field(RECIPIENT)
                    .ccRecipient(USER_1_FULL_ADDRESS)
                    .ccRecipient(USER_2_FULL_ADDRESS)
                    .toRecipient(USER_3_FULL_ADDRESS)
                    .toRecipient(USER_4_FULL_ADDRESS)
                    .valueToMatch(SHOULD_NOT_MATCH)
                    .build(),
                argumentBuilder().description("not matching bcc headers")
                    .field(RECIPIENT)
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

    @ParameterizedTest(name = "CONTAINS should match for field {1}: {0}")
    @MethodSource("containsTestSuite")
    void matchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
    }

    @ParameterizedTest(name = "CONTAINS should not match for field {1}: {0}")
    @MethodSource("notContainsTestSuite")
    void notMatchingContainsTest(String testDescription,
                              Rule.Condition.Field fieldToMatch,
                              MimeMessageBuilder mimeMessageBuilder,
                              String valueToMatch,
                              JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEmpty();
    }

    @ParameterizedTest(name = "NOT-CONTAINS should match for field {1}: {0}")
    @MethodSource("notContainsTestSuite")
    void matchingNotContainsTest(String testDescription,
                                 Rule.Condition.Field fieldToMatch,
                                 MimeMessageBuilder mimeMessageBuilder,
                                 String valueToMatch,
                                 JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
    }


    @ParameterizedTest(name = "NOT-CONTAINS should not match for field {1}: {0}")
    @MethodSource("containsTestSuite")
    void notContainsNotMatchingTest(String testDescription,
                                    Rule.Condition.Field fieldToMatch,
                                    MimeMessageBuilder mimeMessageBuilder,
                                    String valueToMatch,
                                    JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_CONTAINS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEmpty();
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should match for field {1}: {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void equalsMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
    }

    @ParameterizedTest(name = "EXACTLY-EQUALS should not match for field {1}: {0}")
    @MethodSource("notEqualsTestSuite")
    void equalsNotMatchingTest(String testDescription,
                            Rule.Condition.Field fieldToMatch,
                            MimeMessageBuilder mimeMessageBuilder,
                            String valueToMatch,
                            JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEmpty();
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should match for field {1}: {0}")
    @MethodSource("notEqualsTestSuite")
    void notEqualsMatchingTest(String testDescription,
                               Rule.Condition.Field fieldToMatch,
                               MimeMessageBuilder mimeMessageBuilder,
                               String valueToMatch,
                               JMAPFilteringTestSystem testSystem) throws Exception {

        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
    }

    @ParameterizedTest(name = "NOT_EXACTLY_EQUALS should not match for field {1}: {0}")
    @MethodSource("exactlyEqualsTestSuite")
    void notMatchingNotEqualsTests(String testDescription,
                                   Rule.Condition.Field fieldToMatch,
                                   MimeMessageBuilder mimeMessageBuilder,
                                   String valueToMatch,
                                   JMAPFilteringTestSystem testSystem) throws Exception {
        testSystem.defineRulesForRecipient1(Rule.Condition.of(fieldToMatch, NOT_EXACTLY_EQUALS, valueToMatch));
        FakeMail mail = testSystem.asMail(mimeMessageBuilder);
        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
            .isEmpty();
    }

    @Nested
    class MultiRuleBehaviourTest {
        @Test
        void mailDirectiveShouldSetLastMatchedRuleWhenMultipleRules(JMAPFilteringTestSystem testSystem) throws Exception {
            MailboxId mailbox1Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox1Id.serialize())))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("2"))
                    .name("rule 2")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, NOT_CONTAINS, USER_1_USERNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox2Id.serialize())))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("3"))
                    .name("rule 3")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(TO, EXACTLY_EQUALS, USER_3_ADDRESS)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(mailbox3Id.serialize())))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .addFrom(USER_2_ADDRESS)
                    .addToRecipient(USER_3_ADDRESS)
                    .setSubject(UNSCRAMBLED_SUBJECT));

            testSystem.getJmapFiltering().service(mail);

            assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEqualTo(new Attribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME, AttributeValue.of(ImmutableList.of(AttributeValue.of("RECIPIENT_1_MAILBOX_3")))));
        }

        @Test
        void mailDirectiveShouldSetLastMatchedMailboxWhenMultipleMailboxes(JMAPFilteringTestSystem testSystem) throws Exception {
            MailboxId mailbox1Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");
            MailboxId mailbox2Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_2");
            MailboxId mailbox3Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_3");

            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of(
                        mailbox3Id.serialize(),
                        mailbox2Id.serialize(),
                        mailbox1Id.serialize()))))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .setSubject(UNSCRAMBLED_SUBJECT));

            testSystem.getJmapFiltering().service(mail);

            assertThat((ImmutableSet) mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME).get().getValue().value())
                .containsOnly(AttributeValue.of("RECIPIENT_1_MAILBOX_3"),
                    AttributeValue.of("RECIPIENT_1_MAILBOX_2"),
                    AttributeValue.of("RECIPIENT_1_MAILBOX_1"));
        }

        @Test
        void rulesWithEmptyMailboxIdsShouldBeSkept(JMAPFilteringTestSystem testSystem) throws Exception {
            MailboxId mailbox1Id = testSystem.createMailbox(RECIPIENT_1_USERNAME, "RECIPIENT_1_MAILBOX_1");

            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of())))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("2"))
                    .name("rule 2")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of(
                        mailbox1Id.serialize()))))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                    .setSubject(UNSCRAMBLED_SUBJECT));

            testSystem.getJmapFiltering().service(mail);

            assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEqualTo(new Attribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME, AttributeValue.of(ImmutableList.of(AttributeValue.of("RECIPIENT_1_MAILBOX_1")))));
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

            assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEmpty();
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

            assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEmpty();
        }
    }

    @Nested
    class UnknownMailboxIds {
        @Test
        void serviceShouldNotThrowWhenUnknownMailboxId(JMAPFilteringTestSystem testSystem) throws Exception {
            String unknownMailboxId = "4242";
            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(unknownMailboxId)))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

            assertThatCode(() -> testSystem.getJmapFiltering().service(mail))
                .doesNotThrowAnyException();
        }

        @Test
        void mailDirectiveShouldNotBeSetWhenUnknownMailboxId(JMAPFilteringTestSystem testSystem) throws Exception {
            String unknownMailboxId = "4242";
            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(unknownMailboxId)))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

            testSystem.getJmapFiltering().service(mail);

            assertThat(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEmpty();
        }

        @Test
        void rulesWithInvalidMailboxIdsShouldBeSkept(JMAPFilteringTestSystem testSystem) throws Exception {
            String unknownMailboxId = "4242";
            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(unknownMailboxId)))
                    .build(),
                Rule.builder()
                    .id(Rule.Id.of("2"))
                    .name("rule 2")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(
                        testSystem.getRecipient1MailboxId().serialize())))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

            testSystem.getJmapFiltering().service(mail);

            assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
        }

        @Test
        void rulesWithMultipleMailboxIdsShouldFallbackWhenInvalidFirstMailboxId(JMAPFilteringTestSystem testSystem) throws Exception {
            String unknownMailboxId = "4242";

            Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
                Optional.empty(),
                Rule.builder()
                    .id(Rule.Id.of("1"))
                    .name("rule 1")
                    .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                    .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(
                        unknownMailboxId,
                        testSystem.getRecipient1MailboxId().serialize())))
                    .build())).block();

            FakeMail mail = testSystem.asMail(mimeMessageBuilder()
                .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

            testSystem.getJmapFiltering().service(mail);

            assertThatAttribute(mail.getAttribute(RECIPIENT_1_USERNAME_ATTRIBUTE_NAME))
                .isEqualTo(RECIPIENT_1_MAILBOX_1_ATTRIBUTE);
        }
    }

    @Test
    void actionShouldSupportReject(JMAPFilteringTestSystem testSystem) throws Exception {
        Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(),
                    false, false, true, ImmutableList.of()))
                .build())).block();

        FakeMail mail = testSystem.asMail(mimeMessageBuilder()
            .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

        testSystem.getJmapFiltering().service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void actionShouldSupportSeen(JMAPFilteringTestSystem testSystem) throws Exception {
        Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(),
                    true, false, false, ImmutableList.of()))
                .build())).block();

        FakeMail mail = testSystem.asMail(mimeMessageBuilder()
            .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

        testSystem.getJmapFiltering().service(mail);

        assertThat(StorageDirective.fromMail(Username.of("recipient1"), mail))
            .isEqualTo(StorageDirective.builder()
                .seen(Optional.of(true))
                .build());
    }

    @Test
    void actionShouldSupportImportant(JMAPFilteringTestSystem testSystem) throws Exception {
        Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(),
                    false, true, false, ImmutableList.of()))
                .build())).block();

        FakeMail mail = testSystem.asMail(mimeMessageBuilder()
            .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

        testSystem.getJmapFiltering().service(mail);

        assertThat(StorageDirective.fromMail(Username.of("recipient1"), mail))
            .isEqualTo(StorageDirective.builder()
                .important(Optional.of(true))
                .build());
    }

    @Test
    void actionShouldSupportKeywords(JMAPFilteringTestSystem testSystem) throws Exception {
        Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(),
                    false, false, false, ImmutableList.of("abc", "def")))
                .build())).block();

        FakeMail mail = testSystem.asMail(mimeMessageBuilder()
            .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

        testSystem.getJmapFiltering().service(mail);

        assertThat(StorageDirective.fromMail(Username.of("recipient1"), mail).getFlags().get().getUserFlags())
            .containsOnly("abc", "def");
    }

    @Test
    void actionShouldCombineFlags(JMAPFilteringTestSystem testSystem) throws Exception {
        Mono.from(testSystem.getFilteringManagement().defineRulesForUser(RECIPIENT_1_USERNAME,
            Optional.empty(),
            Rule.builder()
                .id(Rule.Id.of("1"))
                .name("rule 1")
                .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND, Rule.Condition.of(FROM, CONTAINS, FRED_MARTIN_FULLNAME)))
                .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(),
                    true, true, false, ImmutableList.of("abc", "def")))
                .build())).block();

        FakeMail mail = testSystem.asMail(mimeMessageBuilder()
            .addFrom(FRED_MARTIN_FULL_SCRAMBLED_ADDRESS));

        testSystem.getJmapFiltering().service(mail);

        Flags expectedFlags = new Flags();
        expectedFlags.add("abc");
        expectedFlags.add("def");
        expectedFlags.add(Flags.Flag.SEEN);
        expectedFlags.add(Flags.Flag.FLAGGED);
        assertThat(StorageDirective.fromMail(Username.of("recipient1"), mail).getFlags().get())
            .isEqualTo(expectedFlags);
    }
}