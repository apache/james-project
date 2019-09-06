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
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MockSmtpBehaviors {
    public static class Builder {
        public static class CommandStep {
            private final Builder backReference;

            CommandStep(Builder backReference) {
                this.backReference = backReference;
            }

            public ConditionStep expect(SMTPCommand command) {
                Preconditions.checkNotNull(command, "'command' should not be null");
                return new ConditionStep(backReference, command);
            }
        }

        public static class ConditionStep {
            public static Condition anyInput() {
                return Condition.MATCH_ALL;
            }

            public static Condition inputContaining(String value) {
                return new Condition.OperatorCondition(Operator.CONTAINS, value);
            }

            private final Builder backReference;
            private final SMTPCommand command;

            ConditionStep(Builder backReference, SMTPCommand command) {
                this.backReference = backReference;
                this.command = command;
            }

            public ResponseStep matching(Condition condition) {
                return new ResponseStep(backReference, command, condition);
            }
        }

        public static class ResponseStep {
            public static Response serviceNotAvailable(String message) {
                return new Response(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, message);
            }

            public static Response doesNotAcceptAnyMail(String message) {
                return new Response(Response.SMTPStatusCode.DOES_NOT_ACCEPT_MAIL_521, message);
            }

            private final Builder backReference;
            private final SMTPCommand command;
            private final Condition condition;

            ResponseStep(Builder backReference, SMTPCommand command, Condition condition) {
                this.backReference = backReference;
                this.command = command;
                this.condition = condition;
            }

            public NumberOfAnswerStep thenRespond(Response response) {
                return new NumberOfAnswerStep(backReference, command, response, condition);
            }
        }

        public static class NumberOfAnswerStep {
            private final Builder backReference;
            private final SMTPCommand command;
            private final Response response;
            private final Condition condition;

            NumberOfAnswerStep(Builder backReference, SMTPCommand command, Response response, Condition condition) {
                this.backReference = backReference;
                this.command = command;
                this.response = response;
                this.condition = condition;
            }

            public Builder anyTimes() {
                return backReference.add(toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));
            }

            public Builder onlySomeTimes(int count) {
                return backReference.add(toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy.times(count)));
            }

            MockSMTPBehavior toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy numberOfAnswersPolicy) {
                return new MockSMTPBehavior(command, condition, response, numberOfAnswersPolicy);
            }
        }

        private final ImmutableList.Builder<MockSMTPBehavior> behaviors;

        public Builder() {
            this.behaviors = ImmutableList.builder();
        }

        public CommandStep addNewBehavior() {
            return new CommandStep(this);
        }

        Builder add(MockSMTPBehavior behavior) {
            this.behaviors.add(behavior);
            return this;
        }

        public MockSmtpBehaviors build() {
            return new MockSmtpBehaviors(behaviors.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<MockSMTPBehavior> behaviorList;

    @JsonCreator
    public MockSmtpBehaviors(List<MockSMTPBehavior> behaviorList) {
        this.behaviorList = ImmutableList.copyOf(behaviorList);
    }

    public MockSmtpBehaviors(MockSMTPBehavior... behaviorList) {
        this(Arrays.asList(behaviorList));
    }

    @JsonValue
    public List<MockSMTPBehavior> getBehaviorList() {
        return behaviorList;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MockSmtpBehaviors) {
            MockSmtpBehaviors that = (MockSmtpBehaviors) o;

            return Objects.equals(this.behaviorList, that.behaviorList);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(behaviorList);
    }
}
