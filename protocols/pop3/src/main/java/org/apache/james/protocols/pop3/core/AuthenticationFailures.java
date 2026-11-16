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

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts the credential failures of a POP3 connection (PASS, APOP and AUTH share the count) in order not to
 * offer an unthrottled password guessing channel: once the limit is reached, the connection gets closed.
 */
public class AuthenticationFailures {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFailures.class);
    private static final ProtocolSession.AttachmentKey<Integer> AUTHENTICATION_FAILURES =
        ProtocolSession.AttachmentKey.of("POP3_AUTHENTICATION_FAILURES", Integer.class);

    // Same limit as the IMAP server
    public static final int MAX_AUTHENTICATION_FAILURES = 3;

    /**
     * Records a credential failure on the session.
     *
     * @return the given failure response, or a response closing the connection once too many failures happened
     */
    public static Response recordFailure(POP3Session session, Response failureResponse) {
        int failures = session.getAttachment(AUTHENTICATION_FAILURES, ProtocolSession.State.Connection).orElse(0) + 1;
        session.setAttachment(AUTHENTICATION_FAILURES, failures, ProtocolSession.State.Connection);
        if (failures >= MAX_AUTHENTICATION_FAILURES) {
            LOGGER.info("Too many POP3 authentication failures, closing the connection");
            POP3Response response = new POP3Response(POP3Response.ERR_RESPONSE, "Too many authentication failures, closing connection");
            response.setEndSession(true);
            return response;
        }
        return failureResponse;
    }

    private AuthenticationFailures() {
    }
}
