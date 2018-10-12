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

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.fge.lambdas.supplier.ThrowingSupplier;

import spark.Request;
import spark.Response;
import spark.Service;

public class ReindexingRoutes implements Routes {
    private static final String BASE_PATH = "/mailboxIndex";
    private static final String USER_PARAM = ":user";
    private static final String MAILBOX_PARAM = ":mailbox";
    private static final String UID_PARAM = ":uid";
    private static final String USER_PATH = BASE_PATH + "/users/" + USER_PARAM;
    private static final String MAILBOX_PATH = USER_PATH + "/mailboxes/" + MAILBOX_PARAM;
    private static final String MESSAGE_PATH = MAILBOX_PATH + "/mails/" + UID_PARAM;

    private final TaskManager taskManager;
    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final ReIndexer reIndexer;
    private final JsonTransformer jsonTransformer;

    @Inject
    public ReindexingRoutes(TaskManager taskManager, MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, ReIndexer reIndexer, JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.reIndexer = reIndexer;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, this::reIndexAll, jsonTransformer);
        service.post(USER_PATH, this::reIndexUser, jsonTransformer);
        service.post(MAILBOX_PATH, this::reIndexMailbox, jsonTransformer);
        service.post(MESSAGE_PATH, this::reIndexMessage, jsonTransformer);
    }

    private TaskIdDto reIndexAll(Request request, Response response) {
        return wrap(request, response,
            () -> reIndexer.reIndex());
    }

    private TaskIdDto reIndexUser(Request request, Response response) {
        return wrap(request, response,
            () -> reIndexer.reIndex(extractUser(request)));
    }

    private TaskIdDto reIndexMailbox(Request request, Response response) {
        return wrap(request, response,
            () -> reIndexer.reIndex(retrievePath(request)));
    }

    private TaskIdDto reIndexMessage(Request request, Response response) {
        return wrap(request, response,
            () -> reIndexer.reIndex(retrievePath(request), extractUid(request)));
    }

    private TaskIdDto wrap(Request request, Response response, ThrowingSupplier<Task> taskGenerator) {
        enforceTaskParameter(request);
        Task task = taskGenerator.get();
        TaskId taskId = taskManager.submit(task);
        return TaskIdDto.respond(response, taskId);
    }

    private User extractUser(Request request) {
        try {
            return User.fromUsername(request.params(USER_PARAM));
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error while parsing 'user'")
                .cause(e)
                .haltError();
        }
    }

    private MailboxPath retrievePath(Request request) throws MailboxException {
        return toMailboxPath(extractUser(request), extractMailboxId(request));
    }

    private MailboxId extractMailboxId(Request request) {
        try {
            return mailboxIdFactory.fromString(request.params(MAILBOX_PARAM));
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error while parsing 'mailbox'")
                .cause(e)
                .haltError();
        }
    }

    private MessageUid extractUid(Request request) {
        try {
            return MessageUid.of(Long.valueOf(request.params(UID_PARAM)));
        } catch (NumberFormatException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'uid' needs to be a parsable long")
                .cause(e)
                .haltError();
        }
    }

    private MailboxPath toMailboxPath(User user, MailboxId mailboxId) throws MailboxException {
        try {
            MailboxSession systemSession = mailboxManager.createSystemSession(user.asString());
            return mailboxManager.getMailbox(mailboxId, systemSession).getMailboxPath();
        } catch (MailboxNotFoundException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("mailbox not found")
                .cause(e)
                .haltError();
        }
    }

    private void enforceTaskParameter(Request request) {
        String task = request.queryParams("task");
        if (!"reIndex".equals(task)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("task query parameter is mandatory. The only supported value is `reIndex`")
                .haltError();
        }
    }
}
