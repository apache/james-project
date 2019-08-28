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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class MockSMTPBehaviorInformation {

    interface RemainingAnswersCounter {

        static RemainingAnswersCounter from(MockSMTPBehavior.NumberOfAnswersPolicy answersPolicy) {
            return answersPolicy.getNumberOfAnswers()
                .<RemainingAnswersCounter>map(AnswersCounter::new)
                .orElseGet(UnlimitedAnswersCounter::new);
        }

        void decrease();

        boolean hasRemainingAnswers();

        Optional<Integer> getValue();
    }

    static class UnlimitedAnswersCounter implements RemainingAnswersCounter {

        @Override
        public void decrease() {
            // do nothing
        }

        @Override
        public boolean hasRemainingAnswers() {
            return true;
        }

        @Override
        public Optional<Integer> getValue() {
            return Optional.empty();
        }
    }

    static class AnswersCounter implements RemainingAnswersCounter {

        static AnswersCounter remains(int remainingCount) {
            return new AnswersCounter(remainingCount);
        }

        private final AtomicInteger remainingCount;

        AnswersCounter(int remainingCount) {
            Preconditions.checkArgument(remainingCount > 0, "remainingCount should be positive");

            this.remainingCount = new AtomicInteger(remainingCount);
        }

        @Override
        public void decrease() {
            remainingCount.updateAndGet(currentCount -> {
                Preconditions.checkState(currentCount > 0,
                    "remainingCount is already being zero, you can not decrease more");

                return currentCount - 1;
            });
        }

        @Override
        public boolean hasRemainingAnswers() {
            return remainingCount.get() > 0;
        }

        @Override
        public Optional<Integer> getValue() {
            return Optional.of(remainingCount.get());
        }
    }

    public static MockSMTPBehaviorInformation from(MockSMTPBehavior behavior) {
        return new MockSMTPBehaviorInformation(
            behavior,
            RemainingAnswersCounter.from(behavior.getNumberOfAnswer()));
    }

    private final MockSMTPBehavior behavior;
    private final RemainingAnswersCounter remainingAnswersCounter;

    MockSMTPBehaviorInformation(MockSMTPBehavior behavior, RemainingAnswersCounter remainingAnswersCounter) {
        Preconditions.checkNotNull(behavior);
        Preconditions.checkNotNull(remainingAnswersCounter);

        this.behavior = behavior;
        this.remainingAnswersCounter = remainingAnswersCounter;
    }

    public void decreaseRemainingAnswers() {
        remainingAnswersCounter.decrease();
    }

    public MockSMTPBehavior getBehavior() {
        return behavior;
    }

    public boolean hasRemainingAnswers() {
        return remainingAnswersCounter.hasRemainingAnswers();
    }

    @VisibleForTesting
    public Optional<Integer> remainingAnswersCounter() {
        return remainingAnswersCounter.getValue();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MockSMTPBehaviorInformation) {
            MockSMTPBehaviorInformation that = (MockSMTPBehaviorInformation) o;

            return Objects.equals(this.behavior, that.behavior)
                && Objects.equals(this.remainingAnswersCounter(), that.remainingAnswersCounter());
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(behavior, remainingAnswersCounter());
    }
}