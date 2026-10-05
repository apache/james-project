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

package org.apache.james.xodus;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.env.Environment;
import spark.Request;
import spark.Response;
import spark.Service;

public class XodusAdminRoutes implements Routes {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusAdminRoutes.class);
    public static final String XODUS_BASE_PATH = "/xodus";

    private final Environment environment;
    private final PersistentEntityStore entityStore;
    private final FileSystem fileSystem;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public XodusAdminRoutes(Environment environment,
                            PersistentEntityStore entityStore,
                            FileSystem fileSystem,
                            TaskManager taskManager,
                            JsonTransformer jsonTransformer) {
        this.environment = environment;
        this.entityStore = entityStore;
        this.fileSystem = fileSystem;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return XODUS_BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest backupTaskFromRequest = this::createBackupTask;
        service.post(XODUS_BASE_PATH + "/backup", backupTaskFromRequest.asRoute(taskManager), jsonTransformer);
        service.get(XODUS_BASE_PATH + "/check", this::checkIntegrity, jsonTransformer);
    }

    private XodusBackupTask createBackupTask(Request request) throws java.io.FileNotFoundException {
        String backupDirParam = request.queryParams("backupDir");
        File backupDir;
        if (backupDirParam != null && !backupDirParam.isBlank()) {
            backupDir = new File(backupDirParam);
        } else {
            backupDir = new File(fileSystem.getBasedir(), "var/backups");
        }
        return new XodusBackupTask(entityStore, backupDir);
    }

    private Object checkIntegrity(Request request, Response response) {
        try {
            boolean envOpen = environment.isOpen();
            long envLocationFreeSpace = new File(environment.getLocation()).getFreeSpace();

            long[] counts = {0, 0, 0};
            if (envOpen) {
                try {
                    entityStore.executeInReadonlyTransaction(txn -> {
                        counts[0] = txn.getAll(XodusBlobStoreDAO.ENTITY_TYPE).size();
                        counts[1] = txn.getAll(XodusUsersDAO.ENTITY_TYPE).size();
                        counts[2] = txn.getAll(XodusDomainList.ENTITY_TYPE).size();
                    });
                } catch (Exception ignored) {
                    // Ignore
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("status", envOpen ? "HEALTHY" : "UNHEALTHY");
            result.put("environmentOpen", envOpen);
            result.put("environmentLocation", environment.getLocation());
            result.put("freeSpaceBytes", envLocationFreeSpace);
            result.put("totalBlobs", counts[0]);
            result.put("totalUsers", counts[1]);
            result.put("totalDomains", counts[2]);

            response.status(HttpStatus.OK_200);
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to check Xodus integrity", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("Check failed: " + e.getMessage())
                .haltError();
        }
    }
}
