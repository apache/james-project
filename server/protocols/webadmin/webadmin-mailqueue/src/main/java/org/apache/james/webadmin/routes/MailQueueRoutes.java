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

package org.apache.james.webadmin.routes;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;

import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Service;

public class MailQueueRoutes implements Routes {

    @VisibleForTesting  static final String BASE_URL = "/mailQueues";
    private final MailQueueFactory<ManageableMailQueue> mailQueueFactory;
    private final JsonTransformer jsonTransformer;

    @Inject
    @VisibleForTesting MailQueueRoutes(MailQueueFactory<ManageableMailQueue> mailQueueFactory, JsonTransformer jsonTransformer) {
        this.mailQueueFactory = mailQueueFactory;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {
        defineListQueues(service);
    }

    @GET
    @ApiOperation(
        value = "Listing existing MailQueues"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineListQueues(Service service) {
        service.get(BASE_URL,
            (request, response) ->
                mailQueueFactory
                    .listCreatedMailQueues()
                    .stream()
                    .map(ManageableMailQueue::getName)
                    .collect(Guavate.toImmutableList()),
            jsonTransformer);
    }
}
