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

import jakarta.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskRunner;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.MailboxMergingRequest;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Service;

public class CassandraMailboxMergingRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMailboxMergingRoutes.class);

    public static final String BASE = "/cassandra/mailbox/merging";

    private final MailboxMergingTaskRunner mailboxMergingTaskRunner;
    private final CassandraId.Factory mailboxIdFactory;
    private final JsonExtractor<MailboxMergingRequest> jsonExtractor;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;
    private final CassandraMailboxCounterDAO counterDAO;

    @Inject
    public CassandraMailboxMergingRoutes(MailboxMergingTaskRunner mailboxMergingTaskRunner, CassandraId.Factory mailboxIdFactory, TaskManager taskManager, JsonTransformer jsonTransformer, CassandraMailboxCounterDAO counterDAO) {
        this.mailboxMergingTaskRunner = mailboxMergingTaskRunner;
        this.mailboxIdFactory = mailboxIdFactory;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.counterDAO = counterDAO;
        this.jsonExtractor = new JsonExtractor<>(MailboxMergingRequest.class);
    }

    @Override
    public String getBasePath() {
        return BASE;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest taskFromRequest = this::mergeMailboxes;
        service.post(BASE, taskFromRequest.asRoute(taskManager), jsonTransformer);
    }

    public Task mergeMailboxes(Request request) throws JsonExtractException {
        LOGGER.debug("Cassandra upgrade launched");
        MailboxMergingRequest mailboxMergingRequest = jsonExtractor.parse(request.body());
        CassandraId originId = mailboxIdFactory.fromString(mailboxMergingRequest.getMergeOrigin());
        CassandraId destinationId = mailboxIdFactory.fromString(mailboxMergingRequest.getMergeDestination());

        long totalMessagesToMove = counterDAO.countMessagesInMailbox(originId).defaultIfEmpty(0L).block();
        return new MailboxMergingTask(mailboxMergingTaskRunner, totalMessagesToMove, originId, destinationId);
    }
}
