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

package org.apache.james.rspamd.task;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.rspamd.RspamdScanner;
import org.apache.james.util.streams.Iterators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RunningOptions {
    interface ClassificationFilter extends Predicate<MessageResult> {
        ClassificationFilter ALL = any -> true;
        ClassificationFilter CLASSIFIED_AS_HAM = new HeaderBasedPredicate("NO");
        ClassificationFilter CLASSIFIED_AS_SPAM = new HeaderBasedPredicate("YES");

        class HeaderBasedPredicate implements ClassificationFilter {
            private final String value;

            public HeaderBasedPredicate(String value) {
                this.value = value;
            }

            @Override
            public boolean test(MessageResult messageResult) {
                try {
                    return Iterators.toStream(messageResult.getHeaders().headers())
                        .filter(header -> header.getName().equalsIgnoreCase(RspamdScanner.FLAG_MAIL.asString()))
                        .findFirst()
                        .map(header -> header.getValue().equalsIgnoreCase(value))
                        // Message was not classified by Rspamd, include it
                        .orElse(true);
                } catch (MailboxException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public static final Optional<Long> DEFAULT_PERIOD = Optional.empty();
    public static final int DEFAULT_MESSAGES_PER_SECOND = 10;
    public static final Duration DEFAULT_RSPAMD_TIMEOUT = Duration.ofSeconds(15);
    public static final double DEFAULT_SAMPLING_PROBABILITY = 1;
    public static final Optional<Boolean> ALL_MESSAGES = Optional.empty();
    public static final RunningOptions DEFAULT = new RunningOptions(DEFAULT_PERIOD, DEFAULT_MESSAGES_PER_SECOND, DEFAULT_SAMPLING_PROBABILITY, ALL_MESSAGES, DEFAULT_RSPAMD_TIMEOUT);

    private final Optional<Long> periodInSecond;
    private final int messagesPerSecond;
    private final double samplingProbability;
    private final Optional<Boolean> classifiedAsSpam;
    private final Duration rspamdTimeout;

    public RunningOptions(Optional<Long> periodInSecond,
                          int messagesPerSecond,
                          double samplingProbability,
                          Optional<Boolean> classifiedAsSpam) {
        this(periodInSecond, messagesPerSecond, samplingProbability, classifiedAsSpam, DEFAULT_RSPAMD_TIMEOUT);
    }


    public RunningOptions(@JsonProperty("periodInSecond") Optional<Long> periodInSecond,
                          @JsonProperty("messagesPerSecond") int messagesPerSecond,
                          @JsonProperty("samplingProbability") double samplingProbability,
                          @JsonProperty("classifiedAsSpam") Optional<Boolean> classifiedAsSpam,
                          @JsonProperty("rspamdTimeoutInSeconds") Duration rspamdTimeout) {
        this.periodInSecond = periodInSecond;
        this.messagesPerSecond = messagesPerSecond;
        this.samplingProbability = samplingProbability;
        this.classifiedAsSpam = classifiedAsSpam;
        this.rspamdTimeout = rspamdTimeout;
    }

    public Optional<Boolean> getClassifiedAsSpam() {
        return classifiedAsSpam;
    }

    public Optional<Long> getPeriodInSecond() {
        return periodInSecond;
    }

    public int getMessagesPerSecond() {
        return messagesPerSecond;
    }

    public double getSamplingProbability() {
        return samplingProbability;
    }

    public long getRspamdTimeoutInSeconds() {
        return rspamdTimeout.toSeconds();
    }

    @JsonIgnore
    public Duration getRspamdTimeout() {
        return rspamdTimeout;
    }

    @JsonIgnore
    public ClassificationFilter correspondingClassificationFilter() {
        return classifiedAsSpam.map(result -> {
            if (result) {
                return ClassificationFilter.CLASSIFIED_AS_SPAM;
            } else {
                return ClassificationFilter.CLASSIFIED_AS_HAM;
            }
        }).orElse(ClassificationFilter.ALL);
    }
}
