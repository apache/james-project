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

import static org.apache.james.linshare.LinshareFixture.ACCOUNT_ENABLED;
import static org.apache.james.linshare.LinshareFixture.TECHNICAL_PERMISSIONS;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class TechnicalAccountGrantPermissionsRequest {

    private final String uuid;
    private final String mail;
    private final String name;
    private final List<String> permissions;
    private final boolean enable;

    public TechnicalAccountGrantPermissionsRequest(TechnicalAccountResponse technicalAccountResponse) {
        this.uuid = technicalAccountResponse.getUuid();
        this.mail = technicalAccountResponse.getMail();
        this.name = technicalAccountResponse.getName();
        this.enable = ACCOUNT_ENABLED;
        this.permissions = ImmutableList.copyOf(TECHNICAL_PERMISSIONS);
    }

    public String getUuid() {
        return uuid;
    }

    public String getMail() {
        return mail;
    }

    public String getName() {
        return name;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @JsonProperty("enable")
    public boolean isEnabled() {
        return enable;
    }
}
