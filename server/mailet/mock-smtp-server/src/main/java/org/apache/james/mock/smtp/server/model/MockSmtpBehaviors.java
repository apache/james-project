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

            public ResponseStep onCommand(SMTPCommand command) {
                Preconditions.checkNotNull(command, "'command' should not be null");
                return new ResponseStep(backReference, command);
            }
        }

        public static class ResponseStep {
            private final Builder backReference;
            private final SMTPCommand command;

            ResponseStep(Builder backReference, SMTPCommand command) {
                this.backReference = backReference;
                this.command = command;
            }

            public ConditionStep respond(Response.SMTPStatusCode statusCode, String message) {
                return new ConditionStep(backReference, command, new Response(statusCode, message));
            }
        }

        public static class ConditionStep {
            private final Builder backReference;
            private final SMTPCommand command;
            private final Response response;

            ConditionStep(Builder backReference, SMTPCommand command, Response response) {
                this.backReference = backReference;
                this.command = command;
                this.response = response;
            }

            public NumberOfAnswerStep forAnyInput() {
                return new NumberOfAnswerStep(backReference, command, response, Condition.MATCH_ALL);
            }

            public NumberOfAnswerStep forInputContaining(String value) {
                return new NumberOfAnswerStep(backReference, command, response, new Condition.OperatorCondition(Operator.CONTAINS, value));
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

            public Builder unlimitedNumberOfAnswer() {
                return backReference.add(toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));
            }

            public Builder onlySomeAnswers(int count) {
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
