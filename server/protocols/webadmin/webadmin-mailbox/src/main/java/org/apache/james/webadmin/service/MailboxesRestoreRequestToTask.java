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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.Parsers;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.publisher.Mono;
import spark.Request;

public class MailboxesRestoreRequestToTask extends TaskFromRequestRegistry.TaskRegistration {

    public static final TaskRegistrationKey TASK_REGISTRATION_KEY = TaskRegistrationKey.of("restore");

    @Inject
    MailboxesRestoreRequestToTask(RestoreService restoreService, UsersRepository usersRepository, BlobStore blobStore) {
        super(TASK_REGISTRATION_KEY,
            request -> toTask(restoreService, usersRepository, blobStore, request));
    }

    private static Task toTask(RestoreService restoreService,
                               UsersRepository usersRepository,
                               BlobStore blobStore,
                               Request request) throws UsersRepositoryException {
        Username username = Parsers.parseUsername(request.params("username"));
        if (!usersRepository.contains(username)) {
            throw ErrorResponder.notFound("User '%s' does not exist", username.asString());
        }

        byte[] data = request.bodyAsBytes();
        if (data.length == 0) {
            throw ErrorResponder.builder()
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Request body must contain the ZIP backup data")
                .haltError();
        }

        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), data, BlobStore.StoragePolicy.LOW_COST)).block();

        Optional<Boolean> force = Optional.of(Boolean.parseBoolean(request.queryParams("force")));

        return new MailboxesRestoreTask(restoreService, username, blobId, force);
    }
}
