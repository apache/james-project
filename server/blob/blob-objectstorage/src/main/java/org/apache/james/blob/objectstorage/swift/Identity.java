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

public final class Identity {
    public static Identity of(TenantName tenant, UserName userName) {
        return new Identity(tenant, userName);
    }

    private final TenantName tenant;
    private final UserName userName;

    private Identity(TenantName tenant, UserName userName) {
        this.tenant = tenant;
        this.userName = userName;
    }

    public String asString() {
        return tenant.value() + ":" + userName.value();
    }

    public TenantName getTenant() {
        return tenant;
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
        Identity identity = (Identity) o;
        return Objects.equal(tenant, identity.tenant) &&
            Objects.equal(userName, identity.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tenant, userName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("tenant", tenant)
            .add("userName", userName)
            .toString();
    }
}
