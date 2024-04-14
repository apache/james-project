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

package org.apache.james.webadmin.routes;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Options;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersTask;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import spark.Request;

public class RecomputeMailboxCountersRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
    private static final TaskRegistrationKey REGISTRATION_KEY = TaskRegistrationKey.of("RecomputeMailboxCounters");
    private static final String TRUST_PARAM = "trustMessageProjection";

    @Inject
    public RecomputeMailboxCountersRequestToTask(RecomputeMailboxCountersService service) {
        super(REGISTRATION_KEY,
            request -> new RecomputeMailboxCountersTask(service, parseOptions(request)));
    }

    private static Options parseOptions(Request request) {
        Optional<String> stringValue = Optional.ofNullable(request.queryParams(TRUST_PARAM));
        return parseOptions(stringValue);
    }

    @VisibleForTesting
    static Options parseOptions(Optional<String> stringValue) {
        return stringValue
            .map(RecomputeMailboxCountersRequestToTask::parseOptions)
            .orElse(Options.recheckMessageProjection());
    }

    private static Options parseOptions(String stringValue) {
        Preconditions.checkArgument(isValid(stringValue), "'%s' needs to be a valid boolean", TRUST_PARAM);
        return Options.of(Boolean.valueOf(stringValue));
    }

    private static boolean isValid(String stringValue) {
        return stringValue.equalsIgnoreCase("true")
            || stringValue.equalsIgnoreCase("false");
    }
}
