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

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.task.Task;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

public class CreateMissingParentsRequestToTask extends TaskFromRequestRegistry.TaskRegistration {

    public static final TaskRegistrationKey TASK_REGISTRATION_KEY = TaskRegistrationKey.of("createMissingParents");

    @Inject
    CreateMissingParentsRequestToTask(MailboxManager mailboxManager) {

        super(TASK_REGISTRATION_KEY,
            request -> toTask(mailboxManager));
    }

    private static Task toTask(MailboxManager mailboxManager) {
        return new CreateMissingParentsTask(mailboxManager);
    }
}
