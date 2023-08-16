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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class BrowseStartUpdateTask implements Task {
    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;
        private final MailQueueName mailQueue;

        public AdditionalInformation(MailQueueName mailQueue, Instant timestamp) {
            this.mailQueue = mailQueue;
            this.timestamp = timestamp;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }


        public MailQueueName getMailQueue() {
            return mailQueue;
        }

    }

    public static final TaskType TYPE = TaskType.of("browse-start-update");

    private final MailQueueName name;
    private final CassandraMailQueueView.Factory cassandraMailQueueView;

    public BrowseStartUpdateTask(MailQueueName name, CassandraMailQueueView.Factory cassandraMailQueueView) {
        this.name = name;
        this.cassandraMailQueueView = cassandraMailQueueView;
    }

    @Override
    public Result run() {
        try {
            CassandraMailQueueView mailQueueView = (CassandraMailQueueView) cassandraMailQueueView.create(name);
            mailQueueView.updateBrowseStart()
                .block();
            return Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.error("Error when republishing mails", e);
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(name, Clock.systemUTC().instant()));
    }

    public MailQueueName getMailQueue() {
        return name;
    }
}
