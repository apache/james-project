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
package org.apache.james.protocols.smtp.core;

import java.nio.ByteBuffer;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.smtp.SMTPSession;

/**
 * Abstract base class which makes it easier to handles lines be providing one method per message part.
 * </br>
 * </br>
 * This is:
 * </br>
 * <strong>headers</strong></br>
 * <strong>separator</strong></br>
 * <strong>body</strong></br>
 * </br>
 * 
 * Subclasses should override at least one of these methods:
 * </br>
 * {@link #onHeadersLine(SMTPSession, ByteBuffer, LineHandler)}</br>
 * {@link #onSeparatorLine(SMTPSession, ByteBuffer, LineHandler)}</br>
 * {@link #onBodyLine(SMTPSession, ByteBuffer, LineHandler)}</br>
 * 
 *
 */
public abstract class SeparatingDataLineFilter implements DataLineFilter {

    private static final ProtocolSession.AttachmentKey<Boolean> HEADERS_COMPLETE = ProtocolSession.AttachmentKey.of("HEADERS_COMPLETE", Boolean.class);
    
    @Override
    public final Response onLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        if (!session.getAttachment(HEADERS_COMPLETE, State.Transaction).isPresent()) {
            if (line.remaining() == 2) {
                if (line.get() == '\r' && line.get() == '\n') {
                    line.rewind();
                    Response response = onSeparatorLine(session, line, next);
                    session.setAttachment(HEADERS_COMPLETE, Boolean.TRUE, State.Transaction);
                    return response;
                }
                line.rewind();
            }
            return onHeadersLine(session, line, next);
        }
        
        return onBodyLine(session, line, next);
    }
    
    /**
     * Gets called when the separating line is received. This is the CLRF sequence. 
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, ByteBuffer)} but subclasses should override it if needed.
     *
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onSeparatorLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
    
    /**
     * Gets called for each received line until the CRLF sequence was received.
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, ByteBuffer)} but subclasses should override it if needed.
     * 
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onHeadersLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
    
    /**
     * Gets called for each received line after the CRLF sequence was received.
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, ByteBuffer)} but subclasses should override it if needed.
     * 
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onBodyLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
}
