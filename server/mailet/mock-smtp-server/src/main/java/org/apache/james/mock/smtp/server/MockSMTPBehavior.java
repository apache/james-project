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

import com.google.common.base.Preconditions;

public class MockSMTPBehavior {
    public static final class NumberOfAnswersPolicy {

        public static NumberOfAnswersPolicy anytime() {
            return new NumberOfAnswersPolicy(Optional.empty());
        }

        public static NumberOfAnswersPolicy times(int times) {
            Preconditions.checkArgument(times > 0, "times should be positive");
            return new NumberOfAnswersPolicy(Optional.of(times));
        }

        private final Optional<Integer> numberOfAnswers;

        private NumberOfAnswersPolicy(Optional<Integer> numberOfAnswers) {
            this.numberOfAnswers = numberOfAnswers;
        }

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

    public SMTPCommand getSmtpCommand() {
        return smtpCommand;
    }

    public Optional<Condition> getCondition() {
        return condition;
    }

    public Response getResponse() {
        return response;
    }

    public NumberOfAnswersPolicy getNumberOfAnswers() {
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
