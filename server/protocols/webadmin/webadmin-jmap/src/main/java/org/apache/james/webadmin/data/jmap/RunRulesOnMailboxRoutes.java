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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.RuleDTO;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.data.jmap.dto.UserTaskResponse;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskHandler;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.validation.MailboxName;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

public class RunRulesOnMailboxRoutes implements Routes {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRulesOnMailboxRoutes.class);

    private static final TaskRegistrationKey TRIAGE = TaskRegistrationKey.of("triage");
    private static final String ACTION_QUERY_PARAM = "action";
    private static final String MAILBOX_NAME_QUERY_PARAM = "mailboxName";
    private static final String MAILBOX_NAME = ":mailboxName";
    private static final String MAILBOXES = "mailboxes";
    private static final String USER_NAME = ":userName";
    private static final String USERS_BASE = "/users";
    public static final String USER_MAILBOXES_BASE = USERS_BASE + SEPARATOR + USER_NAME + SEPARATOR + MAILBOXES;
    public static final String SPECIFIC_MAILBOX = USER_MAILBOXES_BASE + SEPARATOR + MAILBOX_NAME;
    public static final String MESSAGES_BASE = "/messages";
    public static final String MESSAGES_PATH = SPECIFIC_MAILBOX + MESSAGES_BASE;

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final RunRulesOnMailboxService runRulesOnMailboxService;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final ObjectMapper jsonDeserialize;

    @Inject
    RunRulesOnMailboxRoutes(UsersRepository usersRepository,
                            MailboxManager mailboxManager,
                            TaskManager taskManager,
                            JsonTransformer jsonTransformer,
                            RunRulesOnMailboxService runRulesOnMailboxService) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.runRulesOnMailboxService = runRulesOnMailboxService;
        this.jsonDeserialize = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule());
    }

    @Override
    public String getBasePath() {
        return USER_MAILBOXES_BASE;
    }

    @Override
    public void define(Service service) {
        service.post(MESSAGES_PATH, runRulesOnMailboxRoute(), jsonTransformer);

        service.post(MESSAGES_BASE, this::runRulesOnAllUsersMailbox, jsonTransformer);
    }

    public Route runRulesOnMailboxRoute() {
        return TaskFromRequestRegistry.builder()
            .parameterName(ACTION_QUERY_PARAM)
            .register(TRIAGE, request -> new TaskHandler.SingleTaskHandler(runRulesOnMailbox(request)))
            .buildAsRoute(taskManager);
    }

    public Task runRulesOnMailbox(Request request) throws UsersRepositoryException, MailboxException {
        Username username = getUsernameParam(request);
        MailboxName mailboxName = new MailboxName(request.params(MAILBOX_NAME));
        try {
            usernamePreconditions(username);
            mailboxExistPreconditions(username, mailboxName);
            RuleDTO ruleDTO = jsonDeserialize.readValue(request.body(), RuleDTO.class);
            Rules rules = new Rules(RuleDTO.toRules(ImmutableList.of(ruleDTO)), Version.INITIAL);

            return new RunRulesOnMailboxTask(username, mailboxName, rules, runRulesOnMailboxService);
        } catch (IllegalStateException e) {
            LOGGER.info("Invalid argument on user mailboxes", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Invalid argument on user mailboxes")
                .cause(e)
                .haltError();
        } catch (JsonProcessingException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("JSON payload of the request is not valid")
                .cause(e)
                .haltError();
        }
    }

    public List<UserTaskResponse> runRulesOnAllUsersMailbox(Request request, Response response) {
        try {
            actionPrecondition(request);
            MailboxName mailboxName = getMailboxNameQueryParam(request);
            RuleDTO ruleDTO = jsonDeserialize.readValue(request.body(), RuleDTO.class);
            Rules rules = new Rules(RuleDTO.toRules(ImmutableList.of(ruleDTO)), Version.INITIAL);
            rulesPrecondition(rules);

            response.status(HttpStatus.CREATED_201);
            return runRulesOnAllUsersMailbox(mailboxName, rules);
        } catch (IllegalStateException e) {
            LOGGER.info("Invalid argument on /messages", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Invalid argument on /messages")
                .cause(e)
                .haltError();
        } catch (JsonProcessingException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("JSON payload of the request is not valid")
                .cause(e)
                .haltError();
        }
    }

    private List<UserTaskResponse> runRulesOnAllUsersMailbox(MailboxName mailboxName, Rules rules) {
        return Flux.from(usersRepository.listReactive())
            .filter(Throwing.predicate(username -> mailboxForUserExists(username, mailboxName)))
            .map(username -> runRulesOnUserMailbox(username, mailboxName, rules))
            .collectList()
            .block();
    }

    private UserTaskResponse runRulesOnUserMailbox(Username username, MailboxName mailboxName, Rules rules) {
        Task task = new RunRulesOnMailboxTask(username, mailboxName, rules, runRulesOnMailboxService);
        TaskId taskId = taskManager.submit(task);
        return new UserTaskResponse(username, taskId);
    }

    private Username getUsernameParam(Request request) {
        return Username.of(request.params(USER_NAME));
    }

    private void usernamePreconditions(Username username) throws UsersRepositoryException {
        Preconditions.checkState(usersRepository.contains(username), "User does not exist");
    }

    private void mailboxExistPreconditions(Username username, MailboxName mailboxName) throws MailboxException {
        Preconditions.checkState(mailboxForUserExists(username, mailboxName), "Mailbox does not exist: " + mailboxName.asString());
    }

    private void actionPrecondition(Request request) {
        Optional<String> action = Optional.ofNullable(request.queryParams(ACTION_QUERY_PARAM));

        if (action.isEmpty() || !action.get().equalsIgnoreCase(TRIAGE.asString())) {
            throw new IllegalArgumentException("'action' query parameter is compulsory. Supported values are [triage]");
        }
    }

    private MailboxName getMailboxNameQueryParam(Request request) {
            return Optional.ofNullable(request.queryParams(MAILBOX_NAME_QUERY_PARAM))
                .map(MailboxName::new)
                .orElseThrow(() -> new IllegalArgumentException("mailboxName query param is missing"));
    }

    private boolean mailboxForUserExists(Username username, MailboxName mailboxName) throws MailboxNameException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
            .assertAcceptable(mailboxSession.getPathDelimiter());
        boolean result = Boolean.TRUE.equals(Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession)).block());
        mailboxManager.endProcessingRequest(mailboxSession);
        return result;
    }

    private void rulesPrecondition(Rules rules) {
        if (rules.getRules()
            .stream()
            .map(Rule::getAction)
            .anyMatch(action -> !action.getAppendInMailboxes().getMailboxIds().isEmpty())) {
            throw new IllegalArgumentException("Rule payload should not have [appendInMailboxes] action defined for runRulesOnAllUsersMailbox route");
        }
    }
}
