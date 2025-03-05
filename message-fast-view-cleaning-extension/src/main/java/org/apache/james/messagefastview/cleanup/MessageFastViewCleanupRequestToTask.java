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

package org.apache.james.messagefastview.cleanup;

import java.util.Optional;

import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

import jakarta.inject.Inject;
import spark.Request;

public class MessageFastViewCleanupRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
    private static final TaskRegistrationKey REGISTRATION_KEY = TaskRegistrationKey.of("MessageFastViewCleanup");
    private static final String MESSAGE_IDS_PER_SECOND = "messageIdsPerSecond";

    @Inject
    public MessageFastViewCleanupRequestToTask(MessageFastViewCleanupService service) {
        super(REGISTRATION_KEY, request -> new MessageFastViewCleanupTask(service,parseRunningOptions(request)));
    }

    private static MessageFastViewCleanupService.RunningOptions parseRunningOptions(Request request) {
        return MessageFastViewCleanupService.RunningOptions.of(intQueryParameter(request).orElse(RunningOptions.DEFAULT_USERS_PER_SECOND));
    }

    private static Optional<Integer> intQueryParameter(Request request) {
        try {
            return Optional.ofNullable(request.queryParams(MESSAGE_IDS_PER_SECOND))
                .map(Integer::parseInt);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s', expecting a " +
                "strictly positive optional integer", MESSAGE_IDS_PER_SECOND), e);
        }
    }
}
