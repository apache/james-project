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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunningOptions {
    public static final Optional<Long> DEFAULT_PERIOD = Optional.empty();
    public static final int DEFAULT_MESSAGES_PER_SECOND = 10;
    public static final double DEFAULT_SAMPLING_PROBABILITY = 1;
    public static final RunningOptions DEFAULT = new RunningOptions(DEFAULT_PERIOD, DEFAULT_MESSAGES_PER_SECOND, DEFAULT_SAMPLING_PROBABILITY);

    private final Optional<Long> periodInSecond;
    private final int messagesPerSecond;
    private final double samplingProbability;

    public RunningOptions(@JsonProperty("periodInSecond") Optional<Long> periodInSecond,
                          @JsonProperty("messagesPerSecond") int messagesPerSecond,
                          @JsonProperty("samplingProbability") double samplingProbability) {
        this.periodInSecond = periodInSecond;
        this.messagesPerSecond = messagesPerSecond;
        this.samplingProbability = samplingProbability;
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
}
