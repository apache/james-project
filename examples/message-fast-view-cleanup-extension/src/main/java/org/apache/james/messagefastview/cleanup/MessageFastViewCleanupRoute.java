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

package org.apache.james.messagefastview.cleanup;

import jakarta.inject.Inject;

import org.apache.james.webadmin.PublicRoutes;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Service;

public class MessageFastViewCleanupRoute implements PublicRoutes {
    private final MessageFastViewCleanupService messageFastViewCleanupService;

    @Inject
    public MessageFastViewCleanupRoute(MessageFastViewCleanupService messageFastViewCleanupService) {
        this.messageFastViewCleanupService = messageFastViewCleanupService;
    }

    @Override
    public String getBasePath() {
        return "/messageFastViewCleanup";
    }

    @Override
    public void define(Service service) {
        service.post(getBasePath(), this::cleanup);
    }

    private String cleanup(Request request, Response response) {
        Long totalDeletedMessageFastViewItems = messageFastViewCleanupService.cleanup().block();
        response.status(HttpStatus.OK_200);
        return String.format("{\"totalDeletedMessageFastViewItems\":\"%s\"}", totalDeletedMessageFastViewItems);
    }
}
