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

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTaskRunner;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.MailboxMergingRequest;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskGenerator;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;
import spark.Request;
import spark.Service;

@Api(tags = "Mailbox merging route for fixing Ghost mailbox bug described in MAILBOX-322")
@Path(":cassandra/mailbox/merging")
@Produces("application/json")
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
        TaskGenerator taskGenerator = this::mergeMailboxes;
        service.post(BASE, taskGenerator.asRoute(taskManager), jsonTransformer);
    }

    @POST
    @ApiOperation("Triggers the merge of 2 mailboxes. Old mailbox Id will no more be accessible, rights and messages will be merged.")
    @ApiImplicitParams(
        {
            @ApiImplicitParam(
                required = true,
                paramType = "body",
                dataTypeClass = MailboxMergingRequest.class,
                example = "{\"oldMailboxId\":\"4555-656-4554\",\"oldMailboxId\":\"9693-665-2500\"}",
                value = "The mailboxes to merge together.")
        })
    @ApiResponses(
        {
            @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task",
                response = TaskIdDto.class, responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Error with supplied data (JSON parsing or invalid mailbox ids)")
        })
    public Task mergeMailboxes(Request request) throws JsonExtractException {
        LOGGER.debug("Cassandra upgrade launched");
        MailboxMergingRequest mailboxMergingRequest = jsonExtractor.parse(request.body());
        CassandraId originId = mailboxIdFactory.fromString(mailboxMergingRequest.getMergeOrigin());
        CassandraId destinationId = mailboxIdFactory.fromString(mailboxMergingRequest.getMergeDestination());

        long totalMessagesToMove = counterDAO.countMessagesInMailbox(originId).defaultIfEmpty(0L).block();
        return new MailboxMergingTask(mailboxMergingTaskRunner, totalMessagesToMove, originId, destinationId);
    }
}
