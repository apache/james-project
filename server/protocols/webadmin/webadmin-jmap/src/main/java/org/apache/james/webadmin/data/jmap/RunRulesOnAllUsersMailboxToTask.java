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

import static org.apache.james.webadmin.data.jmap.RunRulesOnMailboxRoutes.TRIAGE;

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
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskHandler;
import org.apache.james.webadmin.tasks.UserTask;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.validation.MailboxName;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;

public class RunRulesOnAllUsersMailboxToTask extends TaskFromRequestRegistry.MultiTaskRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRulesOnAllUsersMailboxToTask.class);
    private static final String MAILBOX_NAME_QUERY_PARAM = "mailboxName";
    private static final ObjectMapper jsonDeserialize = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule());

    @Inject
    RunRulesOnAllUsersMailboxToTask(UsersRepository usersRepository,
                                    MailboxManager mailboxManager,
                                    RunRulesOnMailboxService runRulesOnMailboxService) {
        super(TRIAGE,
            request -> new TaskHandler.MultiTaskHandler(runRulesOnAllUsersMailbox(request, usersRepository, mailboxManager, runRulesOnMailboxService)));
    }

    private static List<UserTask> runRulesOnAllUsersMailbox(Request request, UsersRepository usersRepository, MailboxManager mailboxManager, RunRulesOnMailboxService runRulesOnMailboxService) {
        try {
            MailboxName mailboxName = getMailboxNameQueryParam(request);
            RuleDTO ruleDTO = jsonDeserialize.readValue(request.body(), RuleDTO.class);
            Rules rules = new Rules(RuleDTO.toRules(ImmutableList.of(ruleDTO)), Version.INITIAL);
            rulesPrecondition(rules);

            return runRulesOnAllUsersMailbox(usersRepository, mailboxManager, runRulesOnMailboxService, mailboxName, rules);
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

    private static List<UserTask> runRulesOnAllUsersMailbox(UsersRepository usersRepository, MailboxManager mailboxManager, RunRulesOnMailboxService runRulesOnMailboxService, MailboxName mailboxName, Rules rules) {
        return Flux.from(usersRepository.listReactive())
            .filter(Throwing.predicate(username -> mailboxForUserExists(mailboxManager, username, mailboxName)))
            .map(username -> runRulesOnUserMailbox(runRulesOnMailboxService, username, mailboxName, rules))
            .collectList()
            .block();
    }

    private static UserTask runRulesOnUserMailbox(RunRulesOnMailboxService runRulesOnMailboxService, Username username, MailboxName mailboxName, Rules rules) {
        Task task = new RunRulesOnMailboxTask(username, mailboxName, rules, runRulesOnMailboxService);
        return new UserTask(username, task);
    }

    private static MailboxName getMailboxNameQueryParam(Request request) {
        return Optional.ofNullable(request.queryParams(MAILBOX_NAME_QUERY_PARAM))
            .map(MailboxName::new)
            .orElseThrow(() -> new IllegalArgumentException("mailboxName query param is missing"));
    }

    private static void rulesPrecondition(Rules rules) {
        if (rules.getRules()
            .stream()
            .map(Rule::getAction)
            .anyMatch(action -> !action.getAppendInMailboxes().getMailboxIds().isEmpty())) {
            throw new IllegalArgumentException("Rule payload should not have [appendInMailboxes] action defined for runRulesOnAllUsersMailbox route");
        }
    }

    private static boolean mailboxForUserExists(MailboxManager mailboxManager, Username username, MailboxName mailboxName) throws MailboxNameException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
            .assertAcceptable(mailboxSession.getPathDelimiter());
        boolean result = Boolean.TRUE.equals(Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession)).block());
        mailboxManager.endProcessingRequest(mailboxSession);
        return result;
    }
}
