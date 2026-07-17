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

import java.nio.charset.StandardCharsets;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.pop3.POP3Session;

/**
 * Preserves the regular POP3 command limit while allowing larger RFC 5034 SASL continuation responses.
 * It also validates the original AUTH command before the shared dispatcher trims trailing whitespace.
 */
public class Pop3CommandDispatcher extends CommandDispatcher<POP3Session> {
    private static final int CRLF_LENGTH = 2;
    private static final int MAXIMUM_AUTH_COMMAND_LENGTH = 255;
    private static final int MAXIMUM_REGULAR_COMMAND_LENGTH = 8192;

    private static boolean isAuthCommand(byte[] line) {
        String input = new String(line, StandardCharsets.US_ASCII).stripLeading();
        int argumentSeparator = input.indexOf(' ');
        String command = argumentSeparator < 0 ? input : input.substring(0, argumentSeparator);
        return command.equalsIgnoreCase("AUTH");
    }

    @Override
    public Response onLine(POP3Session session, byte[] line) {
        // The Netty decoder admits longer SASL continuations; regular commands retain the previous POP3 limit.
        if (line.length > MAXIMUM_REGULAR_COMMAND_LENGTH + CRLF_LENGTH) {
            return session.newLineTooLongResponse();
        }
        return super.onLine(session, line);
    }

    @Override
    protected Request parseRequest(POP3Session session, byte[] line) throws Exception {
        // RFC 5034 section 4 keeps RFC 2449's 255-octet command limit, including the terminating CRLF,
        // for the complete AUTH command. Validate before the generic parser trims input.
        if (isAuthCommand(line) && line.length > MAXIMUM_AUTH_COMMAND_LENGTH) {
            throw new IllegalArgumentException("POP3 AUTH command exceeds 255 octets");
        }
        return super.parseRequest(session, line);
    }
}
