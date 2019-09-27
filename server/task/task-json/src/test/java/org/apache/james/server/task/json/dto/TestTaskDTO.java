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

package org.apache.james.server.task.json.dto;

import org.apache.james.server.task.json.TestTask;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TestTaskDTO implements TaskDTO {
    private final long parameter;
    private final String type;

    public TestTaskDTO(@JsonProperty("type") String type, @JsonProperty("parameter") long parameter) {
        this.type = type;
        this.parameter = parameter;
    }

    public long getParameter() {
        return parameter;
    }

    @Override
    public String getType() {
        return type;
    }

    @JsonIgnore
    public TestTask toTask() {
        return new TestTask(parameter);
    }


}
