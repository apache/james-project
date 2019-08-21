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

package org.apache.james.mock.smtp.server;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = MockSMTPBehavior.Builder.class)
public class MockSMTPBehavior {
    public static final class NumberOfAnswersPolicy {

        public static NumberOfAnswersPolicy anytime() {
            return new NumberOfAnswersPolicy(Optional.empty());
        }

        @JsonCreator
        public static NumberOfAnswersPolicy times(int times) {
            Preconditions.checkArgument(times > 0, "times should be positive");
            return new NumberOfAnswersPolicy(Optional.of(times));
        }

        private final Optional<Integer> numberOfAnswers;

        private NumberOfAnswersPolicy(Optional<Integer> numberOfAnswers) {
            this.numberOfAnswers = numberOfAnswers;
        }

        @JsonValue
        public Optional<Integer> getNumberOfAnswers() {
            return numberOfAnswers;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof NumberOfAnswersPolicy) {
                NumberOfAnswersPolicy that = (NumberOfAnswersPolicy) o;

                return Objects.equals(this.numberOfAnswers, that.numberOfAnswers);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(numberOfAnswers);
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private SMTPCommand smtpCommand;
        private Optional<Condition> condition;
        private Response response;
        private Optional<NumberOfAnswersPolicy> numberOfAnswers;

        public Builder() {
            condition = Optional.empty();
            numberOfAnswers = Optional.empty();
        }

        public Builder command(SMTPCommand command) {
            this.smtpCommand = command;
            return this;
        }

        public Builder response(Response response) {
            this.response = response;
            return this;
        }

        public Builder condition(Condition condition) {
            this.condition = Optional.of(condition);
            return this;
        }

        public Builder numberOfAnswer(Optional<NumberOfAnswersPolicy> numberOfAnswers) {
            this.numberOfAnswers = numberOfAnswers;
            return this;
        }

        public MockSMTPBehavior build() {
            Preconditions.checkState(smtpCommand != null, "You need to specify an smtpCommand");
            Preconditions.checkState(response != null, "You need to specify a response");

            return new MockSMTPBehavior(
                smtpCommand,
                condition,
                response,
                numberOfAnswers.orElse(NumberOfAnswersPolicy.anytime()));
        }
    }

    private final SMTPCommand smtpCommand;
    private final Optional<Condition> condition;
    private final Response response;
    private final NumberOfAnswersPolicy numberOfAnswers;

    public MockSMTPBehavior(SMTPCommand smtpCommand, Optional<Condition> condition, Response response, NumberOfAnswersPolicy numberOfAnswers) {
        Preconditions.checkNotNull(smtpCommand);
        Preconditions.checkNotNull(condition);
        Preconditions.checkNotNull(response);
        Preconditions.checkNotNull(numberOfAnswers);

        this.smtpCommand = smtpCommand;
        this.condition = condition;
        this.response = response;
        this.numberOfAnswers = numberOfAnswers;
    }

    public SMTPCommand getCommand() {
        return smtpCommand;
    }

    public Optional<Condition> getCondition() {
        return condition;
    }

    public Response getResponse() {
        return response;
    }

    public NumberOfAnswersPolicy getNumberOfAnswer() {
        return numberOfAnswers;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MockSMTPBehavior) {
            MockSMTPBehavior that = (MockSMTPBehavior) o;

            return Objects.equals(this.smtpCommand, that.smtpCommand)
                && Objects.equals(this.condition, that.condition)
                && Objects.equals(this.response, that.response)
                && Objects.equals(this.numberOfAnswers, that.numberOfAnswers);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(smtpCommand, condition, response, numberOfAnswers);
    }
}
