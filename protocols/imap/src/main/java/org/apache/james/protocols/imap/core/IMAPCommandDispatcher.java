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
package org.apache.james.protocols.imap.core;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.MultiLineHandler;
import org.apache.james.protocols.imap.IMAPRequest;
import org.apache.james.protocols.imap.IMAPSession;

public class IMAPCommandDispatcher extends CommandDispatcher<IMAPSession> {

    private static final Pattern LITERAL_PATTERN = Pattern.compile(".*\\{(\\d+)\\}.*");
    
    @Override
    protected Request parseRequest(IMAPSession session, ByteBuffer buffer) throws Exception {
        IMAPRequest request = new IMAPRequest(buffer);
        Matcher matcher = LITERAL_PATTERN.matcher(request.getArgument());
        if (matcher.matches()) {
            final long bytesToRead = Long.parseLong(matcher.group(1));
            MultiLineHandler<IMAPSession> handler = new MultiLineHandler<IMAPSession>() {
                
                private static final String BYTES_READ = "BYTES_READ";

                @Override
                protected boolean isReady(IMAPSession session, ByteBuffer line) {
                    long bytesRead = (Long) session.setAttachment(BYTES_READ, null, State.Transaction);
                    bytesRead += line.remaining();
                    if (bytesRead >= bytesToRead) {
                        return true;
                    } else {
                        session.setAttachment(BYTES_READ, bytesRead, State.Transaction);
                        return false;
                    }
                }

                @Override
                protected Response onLines(IMAPSession session, Collection<ByteBuffer> lines) {
                    session.popLineHandler();
                    return dispatchCommandHandlers(session, new IMAPRequest(lines));
                }
            };
            buffer.rewind();
            
            // push the line to the handler
            handler.onLine(session, buffer);
            
            session.pushLineHandler(handler);
            return null;
            
        } else {
            return request;
        }
    }
}
