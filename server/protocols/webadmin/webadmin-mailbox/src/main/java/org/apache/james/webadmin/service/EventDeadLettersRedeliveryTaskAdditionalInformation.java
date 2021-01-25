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
package org.apache.james.webadmin.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.task.TaskExecutionDetails;

public class EventDeadLettersRedeliveryTaskAdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
    private final long successfulRedeliveriesCount;
    private final long failedRedeliveriesCount;
    private final Optional<Group> group;
    private final Optional<EventDeadLetters.InsertionId> insertionId;
    private final Instant timestamp;

    EventDeadLettersRedeliveryTaskAdditionalInformation(long successfulRedeliveriesCount,
                                                        long failedRedeliveriesCount,
                                                        Optional<Group> group,
                                                        Optional<EventDeadLetters.InsertionId> insertionId,
                                                        Instant timestamp) {
        this.successfulRedeliveriesCount = successfulRedeliveriesCount;
        this.failedRedeliveriesCount = failedRedeliveriesCount;
        this.group = group;
        this.insertionId = insertionId;
        this.timestamp = timestamp;
    }

    public long getSuccessfulRedeliveriesCount() {
        return successfulRedeliveriesCount;
    }

    public long getFailedRedeliveriesCount() {
        return failedRedeliveriesCount;
    }

    public Optional<String> getGroup() {
        return group.map(Group::asString);
    }

    public Optional<String> getInsertionId() {
        return insertionId.map(insertionId -> insertionId.getId().toString());
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }
}
