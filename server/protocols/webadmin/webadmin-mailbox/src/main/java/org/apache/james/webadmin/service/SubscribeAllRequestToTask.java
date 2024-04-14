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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;

public class SubscribeAllRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
    public static final TaskRegistrationKey TASK_REGISTRATION_KEY = TaskRegistrationKey.of("subscribeAll");

    @Inject
    SubscribeAllRequestToTask(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, UsersRepository usersRepository) {
        super(TASK_REGISTRATION_KEY,
            request -> toTask(mailboxManager, subscriptionManager, usersRepository, request));
    }

    private static Task toTask(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, UsersRepository usersRepository, Request request) throws UsersRepositoryException {
        Username username = Username.of(request.params("username"));
        if (usersRepository.contains(username)) {
            return new SubscribeAllTask(mailboxManager, subscriptionManager, username);
        }

        throw ErrorResponder.builder()
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .statusCode(HttpStatus.NOT_FOUND_404)
            .message(String.format("User '%s' does not exist", username.asString()))
            .haltError();
    }
}
