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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Map;

import org.apache.james.jmap.methods.Method;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetFilterResponse implements Method.Response {
    public static final String SINGLETON = "singleton";

    public static SetFilterResponse updated() {
        return new SetFilterResponse(ImmutableList.of(SINGLETON), ImmutableMap.of());
    }

    public static SetFilterResponse notUpdated(SetError error) {
        return new SetFilterResponse(ImmutableList.of(), ImmutableMap.of(SINGLETON, error));
    }

    private final List<String> updated;
    private final Map<String, SetError> notUpdated;

    private SetFilterResponse(List<String> updated, Map<String, SetError> notUpdated) {
        this.updated = updated;
        this.notUpdated = notUpdated;
    }

    public List<String> getUpdated() {
        return updated;
    }

    public Map<String, SetError> getNotUpdated() {
        return notUpdated;
    }
}
