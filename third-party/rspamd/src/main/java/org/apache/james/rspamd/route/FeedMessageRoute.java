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

package org.apache.james.rspamd.route;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.task.FeedHamToRspamdTask;
import org.apache.james.rspamd.task.FeedSpamToRspamdTask;
import org.apache.james.rspamd.task.RunningOptions;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;

import com.google.common.base.Preconditions;

import spark.Request;
import spark.Service;

public class FeedMessageRoute implements Routes {
    public static final String BASE_PATH = "/rspamd";
    private static final String REPORT_SPAM_PARAM = "reportSpam";
    private static final String REPORT_HAM_PARAM = "reportHam";

    private final TaskManager taskManager;
    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final UsersRepository usersRepository;
    private final RspamdHttpClient rspamdHttpClient;
    private final RspamdClientConfiguration rspamdConfiguration;
    private final JsonTransformer jsonTransformer;
    private final Clock clock;

    @Inject
    public FeedMessageRoute(TaskManager taskManager, MailboxManager mailboxManager, UsersRepository usersRepository, RspamdHttpClient rspamdHttpClient,
                            JsonTransformer jsonTransformer, Clock clock, MessageIdManager messageIdManager, MailboxSessionMapperFactory mapperFactory,
                            RspamdClientConfiguration rspamdConfiguration) {
        this.taskManager = taskManager;
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
        this.rspamdHttpClient = rspamdHttpClient;
        this.jsonTransformer = jsonTransformer;
        this.clock = clock;
        this.messageIdManager = messageIdManager;
        this.mapperFactory = mapperFactory;
        this.rspamdConfiguration = rspamdConfiguration;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest feedMessageTaskRequest = this::feedMessageTaskFromRequest;
        service.post(BASE_PATH, feedMessageTaskRequest.asRoute(taskManager), jsonTransformer);
    }

    public Task feedMessageTaskFromRequest(Request request) {
        Preconditions.checkArgument(Optional.ofNullable(request.queryParams("action"))
                .filter(action -> action.equals(REPORT_SPAM_PARAM) || action.equals(REPORT_HAM_PARAM))
                .isPresent(), String.format("'action' is missing or must be '%s' or '%s'", REPORT_SPAM_PARAM, REPORT_HAM_PARAM));

        return Optional.ofNullable(request.queryParams("action"))
            .filter(action -> action.equals(REPORT_SPAM_PARAM))
            .map(any -> (Task) new FeedSpamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, getRunningOptions(request), clock, rspamdConfiguration))
            .orElse(new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, getRunningOptions(request), clock, rspamdConfiguration));
    }

    private RunningOptions getRunningOptions(Request request) {
        Optional<Long> periodInSecond = getPeriod(request);
        int messagesPerSecond = getMessagesPerSecond(request).orElse(RunningOptions.DEFAULT_MESSAGES_PER_SECOND);
        double samplingProbability = getSamplingProbability(request).orElse(RunningOptions.DEFAULT_SAMPLING_PROBABILITY);
        Optional<Boolean> classifiedAsSpam = getClassifiedAsSpam(request);
        Duration rspamdTimeout = ParametersExtractor.extractDuration(request, "rspamdTimeout").orElse(RunningOptions.DEFAULT_RSPAMD_TIMEOUT);
        return new RunningOptions(periodInSecond, messagesPerSecond, samplingProbability, classifiedAsSpam, rspamdTimeout);
    }

    private Optional<Long> getPeriod(Request req) {
        return Optional.ofNullable(req.queryParams("period"))
            .filter(Predicate.not(String::isEmpty))
            .map(rawString -> DurationParser.parse(rawString, ChronoUnit.SECONDS).toSeconds())
            .map(period -> {
                Preconditions.checkArgument(period > 0,
                    "'period' must be strictly positive");
                return period;
            });
    }

    private Optional<Integer> getMessagesPerSecond(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("messagesPerSecond"))
                .map(Integer::parseInt)
                .map(messagesPerSecond -> {
                    Preconditions.checkArgument(messagesPerSecond > 0,
                        "'messagesPerSecond' must be strictly positive");
                    return messagesPerSecond;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'messagesPerSecond' must be numeric");
        }
    }

    private Optional<Double> getSamplingProbability(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("samplingProbability"))
                .map(Double::parseDouble)
                .map(samplingProbability -> {
                    Preconditions.checkArgument(samplingProbability >= 0 && samplingProbability <= 1,
                        "'samplingProbability' must be greater than or equal to 0.0 and smaller than or equal to 1.0");
                    return samplingProbability;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'samplingProbability' must be numeric");
        }
    }

    private Optional<Boolean> getClassifiedAsSpam(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("classifiedAsSpam"))
                .map(Boolean::parseBoolean);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'classifiedAsSpam' must be a boolean (true|false)");
        }
    }
}

