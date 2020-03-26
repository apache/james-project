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

package org.apache.james.jmap.http;

import static org.apache.james.util.ReactorUtils.context;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.MDCBuilder;

import reactor.netty.http.server.HttpServerRequest;
import reactor.util.context.Context;

public interface LoggingHelper {
    static Context jmapAuthContext(MailboxSession session) {
        return context("JMAP_AUTH",
            MDCBuilder.of(MDCBuilder.USER, session.getUser().asString()));
    }

    static Context jmapContext(HttpServerRequest req) {
        return context("JMAP", MDCBuilder.create()
            .addContext(MDCBuilder.PROTOCOL, "JMAP")
            .addContext(MDCBuilder.IP, req.hostAddress().getHostString()));
    }

    static Context jmapAction(String action) {
        return context("JMAP_ACTION",
            MDCBuilder.of(MDCBuilder.ACTION, action));
    }
}
