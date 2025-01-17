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

package org.apache.james;

import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;

import com.google.common.base.Preconditions;

public interface DisconnectorNotifier {

    sealed interface Request permits MultipleUserRequest, AllUsersRequest {
    }

    record MultipleUserRequest(Set<Username> usernameList) implements Request {
        public static MultipleUserRequest of(Username username) {
            return new MultipleUserRequest(Set.of(username));
        }

        public static MultipleUserRequest of(Set<Username> usernameList) {
            return new MultipleUserRequest(usernameList);
        }

        public MultipleUserRequest {
            Preconditions.checkArgument(usernameList != null && !usernameList.isEmpty(), "usernameList should not be empty");
        }

        public boolean contains(Username username) {
            return usernameList.contains(username);
        }
    }

    record AllUsersRequest() implements Request {
        public static AllUsersRequest ALL_USERS_REQUEST = new AllUsersRequest();
    }

    void disconnect(Request request);

    class InVMDisconnectorNotifier implements DisconnectorNotifier {
        private final Disconnector disconnector;

        @Inject
        public InVMDisconnectorNotifier(Disconnector disconnector) {
            this.disconnector = disconnector;
        }

        @Override
        public void disconnect(Request request) {
            switch (request) {
                case MultipleUserRequest multipleUserRequest:
                    disconnector.disconnect(multipleUserRequest::contains);
                    break;
                case AllUsersRequest allUsersRequest:
                    disconnector.disconnect(username -> true);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + request);
            }
        }
    }
}
