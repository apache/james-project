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

package org.apache.james.mock.smtp.server.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@JsonDeserialize(builder = Mail.Builder.class)
public class Mail {
    @JsonDeserialize(builder = Parameter.Builder.class)
    public static class Parameter {
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String name;
            private String value;

            public Builder() {

            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            public Parameter build() {
                Preconditions.checkState(name != null, "'name' field cannot be omitted");
                Preconditions.checkState(value != null, "'value' field cannot be omitted");

                return new Parameter(name, value);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Collection<Mail.Parameter> fromArgLine(String argLine) {
            return Splitter.on(' ').splitToStream(argLine)
                .filter(argString -> argString.contains("="))
                .map(Parameter::fromString)
                .collect(ImmutableList.toImmutableList());
        }

        public static Parameter fromString(String argString) {
            Preconditions.checkArgument(argString.contains("="));
            int index = argString.indexOf('=');

            return Mail.Parameter.builder()
                .name(argString.substring(0, index))
                .value(argString.substring(index + 1))
                .build();
        }

        private final String name;
        private final String value;

        private Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Parameter) {
                Parameter that = (Parameter) o;

                return Objects.equals(this.name, that.name)
                    && Objects.equals(this.value, that.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("value", value)
                .toString();
        }
    }

    @JsonDeserialize(builder = Recipient.Builder.class)
    public static class Recipient {
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private MailAddress address;
            private ImmutableList.Builder<Parameter> parameters;

            public Builder() {
                parameters = new ImmutableList.Builder<>();
            }

            public Builder address(MailAddress address) {
                this.address = address;
                return this;
            }

            public Builder addParameter(Parameter parameter) {
                this.parameters.add(parameter);
                return this;
            }

            public Builder parameters(Collection<Parameter> parameters) {
                this.parameters.addAll(parameters);
                return this;
            }

            public Recipient build() {
                Preconditions.checkState(address != null, "'address' field cannot be omitted");

                return new Recipient(address, parameters.build());
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Recipient of(MailAddress address) {
            return new Recipient(address, ImmutableList.of());
        }

        private final MailAddress address;
        private final List<Parameter> parameters;

        private Recipient(MailAddress address, List<Parameter> parameters) {
            this.address = address;
            this.parameters = parameters;
        }

        public MailAddress getAddress() {
            return address;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Recipient) {
                Recipient that = (Recipient) o;

                return Objects.equals(this.address, that.address)
                    && Objects.equals(this.parameters, that.parameters);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(address, parameters);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("address", address)
                .add("parameters", parameters)
                .toString();
        }
    }

    @JsonDeserialize(builder = Mail.Envelope.Builder.class)
    public static class Envelope {

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private MailAddress from;
            private ImmutableSet.Builder<Recipient> recipients;
            private ImmutableSet.Builder<Parameter> mailParameters;

            public Builder() {
                recipients = new ImmutableSet.Builder<>();
                mailParameters = new ImmutableSet.Builder<>();
            }

            public Builder from(MailAddress from) {
                this.from = from;
                return this;
            }

            public Builder from(String from) {
                if (from.equals("<>")) {
                    this.from = MailAddress.nullSender();
                } else {
                    try {
                        this.from = new MailAddress(from);
                    } catch (AddressException e) {
                        throw new RuntimeException(e);
                    }
                }
                return this;
            }

            public Builder addRecipientMailAddress(MailAddress mailAddress) {
                this.recipients.add(Recipient.of(mailAddress));
                return this;
            }

            public Builder addMailParameter(Parameter parameter) {
                this.mailParameters.add(parameter);
                return this;
            }

            public Builder mailParameters(Collection<Parameter> parameters) {
                this.mailParameters.addAll(parameters);
                return this;
            }

            public Builder addRecipient(Recipient recipient) {
                this.recipients.add(recipient);
                return this;
            }

            public Builder recipients(List<Recipient> recipients) {
                this.recipients.addAll(recipients);
                return this;
            }

            public Envelope build() {
                return new Envelope(from, recipients.build(), mailParameters.build());
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Envelope ofAddresses(MailAddress from, MailAddress... recipients) {
            return new Envelope(from, Stream.of(recipients)
                .map(Recipient::of)
                .collect(ImmutableSet.toImmutableSet()), ImmutableSet.of());
        }

        public static Envelope of(MailAddress from, Recipient... recipients) {
            return new Envelope(from, ImmutableSet.copyOf(Arrays.asList(recipients)), ImmutableSet.of());
        }

        private final MailAddress from;
        private final Set<Recipient> recipients;
        private final Set<Parameter> mailParameters;

        private Envelope(MailAddress from, Set<Recipient> recipients, Set<Parameter> mailParameters) {
            this.mailParameters = mailParameters;
            Preconditions.checkNotNull(from);
            Preconditions.checkNotNull(recipients);
            Preconditions.checkArgument(!recipients.isEmpty(), "'recipients' field should not be empty");

            this.from = from;
            this.recipients = recipients;
        }

        public MailAddress getFrom() {
            return from;
        }

        public Set<Recipient> getRecipients() {
            return recipients;
        }

        public Set<Parameter> getMailParameters() {
            return mailParameters;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Envelope) {
                Envelope envelope = (Envelope) o;

                return Objects.equals(this.from, envelope.from)
                    && Objects.equals(this.recipients, envelope.recipients)
                    && Objects.equals(this.mailParameters, envelope.mailParameters);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(from, recipients, mailParameters);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("from", from)
                .add("recipients", recipients)
                .add("mailParameters", mailParameters)
                .toString();
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        @JsonUnwrapped
        private Envelope envelope;
        private String message;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder envelope(Envelope envelope) {
            this.envelope = envelope;
            return this;
        }

        public Mail build() {
            return new Mail(envelope, message);
        }
    }

    @JsonUnwrapped
    private final Envelope envelope;
    private final String message;

    public Mail(Envelope envelope, String message) {
        Preconditions.checkNotNull(envelope);
        Preconditions.checkNotNull(message);

        this.envelope = envelope;
        this.message = message;
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Mail) {
            Mail mail = (Mail) o;

            return Objects.equals(this.envelope, mail.envelope)
                && Objects.equals(this.message, mail.message);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(envelope, message);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("envelope", envelope)
            .add("message", message)
            .toString();
    }
}
