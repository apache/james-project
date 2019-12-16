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

package org.apache.james.webadmin.integration.vault;

import static org.hamcrest.Matchers.is;

import io.restassured.specification.RequestSpecification;

class DeletedMessagesVaultRequests {

    static void exportVaultContent(RequestSpecification webAdminApi, ExportRequest exportRequest) {
        String taskId =
            webAdminApi.with()
                .queryParam("action", "export")
                .queryParam("exportTo", exportRequest.getSharee())
                .body(exportRequest.getMatchingQuery())
                .post("/deletedMessages/users/" + exportRequest.getUserExportFrom())
            .jsonPath()
                .get("taskId");

        webAdminApi.with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    static void restoreMessagesForUserWithQuery(RequestSpecification webAdminApi, String user, String criteria) {
        String taskId = webAdminApi.with()
            .body(criteria)
            .post("/deletedMessages/users/" + user + "?action=restore")
        .jsonPath()
            .get("taskId");

        webAdminApi.given()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    static void purgeVault(RequestSpecification webAdminApi) {
        String taskId =
            webAdminApi.with()
                .queryParam("scope", "expired")
                .delete("/deletedMessages")
            .jsonPath()
                .get("taskId");

        webAdminApi.with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    static void deleteFromVault(RequestSpecification webAdminApi, String user, String messageId) {
        String taskId =
            webAdminApi.with()
                .delete("/deletedMessages/users/" + user + "/messages/" + messageId)
            .jsonPath()
                .get("taskId");

        webAdminApi.with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }
}
