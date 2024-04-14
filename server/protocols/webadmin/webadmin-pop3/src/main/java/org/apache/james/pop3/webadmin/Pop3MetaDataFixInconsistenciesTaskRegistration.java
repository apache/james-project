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

package org.apache.james.pop3.webadmin;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.RunningOptions;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesTask;
import org.apache.james.task.Task;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

import com.google.common.base.Preconditions;

import spark.Request;

public class Pop3MetaDataFixInconsistenciesTaskRegistration extends TaskRegistration {
    private static final TaskRegistrationKey REGISTRATION_KEY = TaskRegistrationKey.of("fixPop3Inconsistencies");

    @Inject
    public Pop3MetaDataFixInconsistenciesTaskRegistration(MetaDataFixInconsistenciesService fixInconsistenciesService) {
        super(REGISTRATION_KEY, request -> fixInconsistencies(fixInconsistenciesService, request));
    }

    private static Task fixInconsistencies(MetaDataFixInconsistenciesService fixInconsistenciesService, Request request) {
        RunningOptions runningOptions = getMessagesPerSecond(request)
            .map(RunningOptions::withMessageRatePerSecond)
            .orElse(RunningOptions.DEFAULT);
        return new MetaDataFixInconsistenciesTask(fixInconsistenciesService, runningOptions);
    }

    private static Optional<Integer> getMessagesPerSecond(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("messagesPerSecond"))
                .map(Integer::parseInt)
                .map(msgPerSeconds -> {
                    Preconditions.checkArgument(msgPerSeconds > 0, "'messagesPerSecond' must be strictly positive");
                    return msgPerSeconds;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'messagesPerSecond' must be numeric");
        }
    }
}