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
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.apache.james.webadmin.data.jmap.dto.UserTask;
import org.apache.james.webadmin.routes.ConditionalRoute;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.validation.MailboxName;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Response;

public class RunRuleOnAllMailboxesRoute implements ConditionalRoute {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuleOnAllMailboxesRoute.class);

    private static final TaskRegistrationKey TRIAGE = TaskRegistrationKey.of("triage");
    private static final String ACTION_QUERY_PARAM = "action";
    private static final String MAILBOX_NAME_QUERY_PARAM = "mailboxName";

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final RunRulesOnMailboxService runRulesOnMailboxService;
    private final TaskManager taskManager;
    private final ObjectMapper jsonDeserialize;

    @Inject
    public RunRuleOnAllMailboxesRoute(UsersRepository usersRepository, MailboxManager mailboxManager, RunRulesOnMailboxService runRulesOnMailboxService, TaskManager taskManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.runRulesOnMailboxService = runRulesOnMailboxService;
        this.taskManager = taskManager;

        this.jsonDeserialize = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule());
    }

    @Override
    public boolean test(Request request) {
        return Optional.ofNullable(request.queryParams(ACTION_QUERY_PARAM))
            .map(TRIAGE.asString()::equalsIgnoreCase)
            .orElse(false);
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        return runRulesOnAllUsersMailbox(request, response);
    }

    public List<UserTask> runRulesOnAllUsersMailbox(Request request, Response response) {
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

    private List<UserTask> runRulesOnAllUsersMailbox(MailboxName mailboxName, Rules rules) {
        return Flux.from(usersRepository.listReactive())
            .filterWhen(username -> mailboxForUserExists(username, mailboxName))
            .flatMap(username -> runRulesOnUserMailbox(username, mailboxName, rules))
            .collectList()
            .block();
    }

    private Mono<UserTask> runRulesOnUserMailbox(Username username, MailboxName mailboxName, Rules rules) {
        Task task = new RunRulesOnMailboxTask(username, mailboxName, rules, runRulesOnMailboxService);

        return Mono.fromCallable(() -> taskManager.submit(task))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .map(taskId -> new UserTask(username, taskId));
    }

    private void actionPrecondition(Request request) {
        if (!test(request)) {
            throw new IllegalArgumentException("'action' query parameter is compulsory. Supported values are [triage]");
        }
    }

    private MailboxName getMailboxNameQueryParam(Request request) {
        return Optional.ofNullable(request.queryParams(MAILBOX_NAME_QUERY_PARAM))
            .map(MailboxName::new)
            .orElseThrow(() -> new IllegalArgumentException("mailboxName query param is missing"));
    }

    private Mono<Boolean> mailboxForUserExists(Username username, MailboxName mailboxName) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString());
        return Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession));
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
