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

package org.apache.james.webadmin.tasks;

import static org.eclipse.jetty.http.HttpHeader.LOCATION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.task.TaskId;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Test;

import spark.Response;

public class TaskIdDtoTest {
    private static final String UID_VALUE = "ce5316cb-c924-40eb-9ca0-c5828e276297";

    @Test
    public void respondShouldReturnCreatedWithTaskIdHeader() {
        Response response = mock(Response.class);
        TaskId taskId = TaskId.fromString(UID_VALUE);

        TaskIdDto.respond(response, taskId);

        verify(response).status(HttpStatus.CREATED_201);
        verify(response).header(LOCATION.asString(), "/tasks/" + UID_VALUE);
        verifyNoMoreInteractions(response);
    }

}