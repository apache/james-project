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

package org.apache.james.mailbox.exception;

import java.util.Optional;

import org.apache.james.core.Username;

public class ForbiddenDelegationException extends MailboxException {

    private final Username givenUser;
    private final Optional<Username> delegateUser;

    public ForbiddenDelegationException(Username givenUser) {
        super("User " + givenUser.asString() + " delegation request cannot be satisfied");
        this.givenUser = givenUser;
        this.delegateUser = Optional.empty();
    }

    public ForbiddenDelegationException(Username givenUser, Username delegateUser) {
        super("User " + givenUser.asString() + " is not delegated by " + delegateUser.asString());
        this.givenUser = givenUser;
        this.delegateUser = Optional.of(delegateUser);
    }

    public Username givenUser() {
        return givenUser;
    }

    public Optional<Username> delegateUser() {
        return delegateUser;
    }
}
