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

package org.apache.james.blob.objectstorage.swift;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public final class IdentityV3 {
    public static IdentityV3 of(DomainName domainName, UserName userName) {
        return new IdentityV3(domainName, userName);
    }

    private final DomainName domainName;
    private final UserName userName;

    private IdentityV3(DomainName domainName, UserName userName) {
        Preconditions.checkArgument(
            domainName != null,
            "Domain name cannot be null");
        Preconditions.checkArgument(
            userName != null,
            "User name cannot be null");
        this.domainName = domainName;
        this.userName = userName;
    }

    public String asString() {
        return domainName.value() + ":" + userName.value();
    }

    public DomainName getDomainName() {
        return domainName;
    }

    public UserName getUserName() {
        return userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdentityV3 identity = (IdentityV3) o;
        return Objects.equal(domainName, identity.domainName) &&
            Objects.equal(userName, identity.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(domainName, userName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("domain", domainName)
            .add("userName", userName)
            .toString();
    }
}
