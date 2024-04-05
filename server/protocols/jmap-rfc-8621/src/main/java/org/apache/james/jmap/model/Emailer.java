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

package org.apache.james.jmap.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = Emailer.Builder.class)
public class Emailer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emailer.class);

    public static List<Emailer> fromAddressList(AddressList list) {
        return Optional.ofNullable(list)
            .map(addresses -> addresses.flatten()
                .stream()
                .map(Emailer::fromMailbox)
                .collect(ImmutableList.toImmutableList()))
            .orElse(ImmutableList.of());
    }

    public static Optional<Emailer> firstFromMailboxList(MailboxList list) {
        return Optional.ofNullable(list)
            .flatMap(mailboxes -> mailboxes.stream()
                .map(Emailer::fromMailbox)
                .findFirst());
    }

    private static Emailer fromMailbox(Mailbox mailbox) {
        return Emailer.builder()
            .name(getNameOrAddress(mailbox))
            .email(mailbox.getAddress())
            .allowInvalid()
            .build();
    }

    private static String getNameOrAddress(Mailbox mailbox) {
        return Optional.ofNullable(mailbox.getName())
            .orElseGet(mailbox::getAddress);
    }


    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private static final boolean DEFAULT_DISABLE = false;

        private Optional<Boolean> allowInvalid = Optional.empty();
        private Optional<String> name = Optional.empty();
        private Optional<String> email = Optional.empty();

        @JsonProperty("name")
        public Builder name(Optional<String> name) {
            this.name = name;
            return this;
        }

        @JsonIgnore
        public Builder name(String name) {
            this.name = Optional.ofNullable(name);
            return this;
        }

        @JsonProperty("email")
        public Builder email(Optional<String> email) {
            this.email = email;
            return this;
        }

        @JsonIgnore
        public Builder email(String email) {
            this.email = Optional.ofNullable(email);
            return this;
        }

        @JsonIgnore
        public Builder allowInvalid() {
            this.allowInvalid = Optional.of(true);
            return this;
        }

        public Emailer build() {
            if (allowInvalid.orElse(DEFAULT_DISABLE)) {
                return buildRelaxed();
            } else {
                return buildStrict();
            }
        }

        private Emailer buildStrict() {
            Preconditions.checkState(name.isPresent(), "'name' is mandatory");
            Preconditions.checkState(email.isPresent(), "'email' is mandatory");
            Preconditions.checkState(!name.get().isEmpty(), "'name' should not be empty");
            Preconditions.checkState(!email.get().isEmpty(), "'email' should not be empty");
            Preconditions.checkState(email.get().contains("@"), "'email' must contain '@' character");
            return new Emailer(name, email);
        }

        private Emailer buildRelaxed() {
            return new Emailer(replaceIfNeeded(name), replaceIfNeeded(email));
        }

        private Optional<String> replaceIfNeeded(Optional<String> value) {
            return value.filter(Predicate.not(Strings::isNullOrEmpty));
        }
    }

    private final Optional<String> name;
    private final Optional<String> email;

    @VisibleForTesting Emailer(Optional<String> name, Optional<String> email) {
        this.name = name;
        this.email = email;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<String> getEmail() {
        return email;
    }

    @JsonIgnore
    public MailAddress toMailAddress() {
        Preconditions.checkArgument(email.isPresent(), "eMailer mail address should be present when sending a mail using JMAP");
        try {
            return new MailAddress(email.get());
        } catch (AddressException e) {
            LOGGER.error("Invalid mail address", email);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Emailer) {
            Emailer otherEMailer = (Emailer) o;
            return Objects.equals(name, otherEMailer.name)
                && Objects.equals(email, otherEMailer.email);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("email", email)
            .toString();
    }
}
