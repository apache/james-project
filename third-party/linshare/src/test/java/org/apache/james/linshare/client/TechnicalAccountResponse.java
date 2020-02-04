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

package org.apache.james.linshare.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class TechnicalAccountResponse {

    private final String uuid;
    private final boolean enabled;
    private final String name;
    private final String mail;
    private final List<String> permissions;

    public TechnicalAccountResponse(@JsonProperty("uuid") String uuid,
                                    @JsonProperty("enable") boolean enabled,
                                    @JsonProperty("name") String name,
                                    @JsonProperty("mail") String mail,
                                    @JsonProperty("permissions") List<String> permissions) {
        this.uuid = uuid;
        this.enabled = enabled;
        this.mail = mail;
        this.name = name;
        this.permissions = ImmutableList.copyOf(permissions);
    }

    public String getUuid() {
        return uuid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getMail() {
        return mail;
    }
}
