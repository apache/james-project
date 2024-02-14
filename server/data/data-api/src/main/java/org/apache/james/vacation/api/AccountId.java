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

package org.apache.james.vacation.api;

import java.util.Objects;

import org.apache.james.core.Username;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class AccountId {

    public static AccountId fromString(String identifier) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(identifier), "AccountId identifier should not be null or empty");
        return new AccountId(identifier);
    }

    public static AccountId fromUsername(Username username) {
        return new AccountId(username.asString());
    }

    private final String identifier;

    private AccountId(String identifier) {
        this.identifier = identifier.toLowerCase();
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AccountId accountId = (AccountId) o;
        return Objects.equals(this.identifier, accountId.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
