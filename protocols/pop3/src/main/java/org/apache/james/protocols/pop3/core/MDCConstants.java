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

package org.apache.james.protocols.pop3.core;

import java.util.Optional;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;

public interface MDCConstants {
    String MAILBOX = "mailbox";
    String ARGUMENT = "argument";
    String STATE = "state";

    static MDCBuilder withMailbox(POP3Session session) {
        return Optional.ofNullable(session.getUserMailbox())
            .map(Throwing.function(Mailbox::getIdentifier).sneakyThrow())
            .map(id -> MDCBuilder.create().addToContext(MAILBOX, id))
            .orElseGet(MDCBuilder::create);
    }

    static MDCBuilder forRequest(Request request) {
        return Optional.ofNullable(request.getArgument())
            .map(argument -> MDCBuilder.create().addToContext(ARGUMENT, argument))
            .orElseGet(MDCBuilder::create);
    }

    static MDCBuilder withSession(POP3Session session) {
        return MDCBuilder.create()
            .addToContext(withMailbox(session))
            .addToContext(STATE, Integer.toString(session.getHandlerState()));
    }
}
