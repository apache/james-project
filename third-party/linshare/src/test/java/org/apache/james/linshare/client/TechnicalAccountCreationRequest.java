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
import static org.apache.james.linshare.LinshareFixture.TECHNICAL_ACCOUNT;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TechnicalAccountCreationRequest {

    private final String name;
    private final String mail;
    private final boolean enable;
    private final String password;
    private final String role;

    public static TechnicalAccountCreationRequest defaultAccount() {
        return new TechnicalAccountCreationRequest(TECHNICAL_ACCOUNT.getUsername(),
            "Technical@linshare.org",
            ACCOUNT_ENABLED,
            TECHNICAL_ACCOUNT.getPassword(),
            "DELEGATION");
    }

    public TechnicalAccountCreationRequest(String name,
                               String mail,
                               boolean enable,
                               String password,
                               String role) {
        this.name = name;
        this.mail = mail;
        this.enable = enable;
        this.password = password;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public String getMail() {
        return mail;
    }

    @JsonProperty("enable")
    public boolean isEnabled() {
        return enable;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

}
