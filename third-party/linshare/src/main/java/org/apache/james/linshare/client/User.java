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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class User {

    private final String uuid;
    private final String domain;
    private final String firstName;
    private final String lastName;
    private final String mail;
    private final String accountType;
    private final boolean external;

    @VisibleForTesting
    User(@JsonProperty("uuid") String uuid,
         @JsonProperty("domain") String domain,
         @JsonProperty("firstName") String firstName,
         @JsonProperty("lastName") String lastName,
         @JsonProperty("mail") String mail,
         @JsonProperty("accountType") String accountType,
         @JsonProperty("external") boolean external) {
        this.uuid = uuid;
        this.domain = domain;
        this.firstName = firstName;
        this.lastName = lastName;
        this.mail = mail;
        this.accountType = accountType;
        this.external = external;
    }

    public String getUuid() {
        return uuid;
    }

    public String getDomain() {
        return domain;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMail() {
        return mail;
    }

    public String getAccountType() {
        return accountType;
    }

    public boolean isExternal() {
        return external;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof User) {
            User user = (User) o;

            return Objects.equals(this.external, user.external)
                && Objects.equals(this.uuid, user.uuid)
                && Objects.equals(this.domain, user.domain)
                && Objects.equals(this.firstName, user.firstName)
                && Objects.equals(this.lastName, user.lastName)
                && Objects.equals(this.mail, user.mail)
                && Objects.equals(this.accountType, user.accountType);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uuid, domain, firstName, lastName, mail, accountType, external);
    }
}