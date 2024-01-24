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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.data.jmap.UploadRepositoryCleanupTask.CleanupScope;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;

import spark.Request;
import spark.Service;

public class JmapUploadRoutes implements Routes {
    public static final String BASE_PATH = "/jmap/uploads";

    private final UploadRepository uploadRepository;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public JmapUploadRoutes(UploadRepository uploadRepository, TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.uploadRepository = uploadRepository;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest cleanupUploadRepositoryTaskRequest = this::cleanupUploadRepository;
        service.delete(BASE_PATH, cleanupUploadRepositoryTaskRequest.asRoute(taskManager), jsonTransformer);
    }

    public Task cleanupUploadRepository(Request request) {
        Optional<CleanupScope> scope = Optional.ofNullable(request.queryParams("scope"))
            .flatMap(CleanupScope::from);
        Preconditions.checkArgument(scope.isPresent(), "'scope' is missing or invalid");
        return new UploadRepositoryCleanupTask(uploadRepository, scope.get());
    }
}
