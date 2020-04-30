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
package org.apache.james.mailbox.store;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;

public class FakeAuthorizator implements Authorizator {

    public static FakeAuthorizator defaultReject() {
        return new FakeAuthorizator(Optional.empty(), Optional.empty());
    }

    public static FakeAuthorizator forUserAndAdmin(Username admin, Username user) {
        return new FakeAuthorizator(Optional.of(admin), Optional.of(user));
    }

    private final Optional<Username> adminId;
    private final Optional<Username> delegatedUserId;

    private FakeAuthorizator(Optional<Username> adminId, Optional<Username> userId) {
        this.adminId = adminId;
        this.delegatedUserId = userId;
    }

    @Override
    public AuthorizationState canLoginAsOtherUser(Username userId, Username otherUserId) {
        if (!adminId.isPresent() || !this.delegatedUserId.isPresent()) {
            return AuthorizationState.NOT_ADMIN;
        }
        if (!adminId.get().equals(userId)) {
            return AuthorizationState.NOT_ADMIN;
        }
        if (!otherUserId.equals(this.delegatedUserId.get())) {
            return AuthorizationState.UNKNOWN_USER;
        }
        return AuthorizationState.ALLOWED;
    }
}

