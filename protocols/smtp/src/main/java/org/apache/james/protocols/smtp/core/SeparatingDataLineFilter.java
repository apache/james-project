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
 * {@link #onHeadersLine(SMTPSession, byte[], LineHandler)}</br>
 * {@link #onSeparatorLine(SMTPSession, byte[], LineHandler)}</br>
 * {@link #onBodyLine(SMTPSession, byte[], LineHandler)}</br>
 * 
 *
 */
public abstract class SeparatingDataLineFilter implements DataLineFilter {
    @Override
    public final Response onLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        if (!session.headerComplete()) {
            if (line.length == 2) {
                if (line[0] == '\r' && line[1] == '\n') {
                    Response response = onSeparatorLine(session, line, next);
                    session.setHeaderComplete(true);
                    return response;
                }
            }
            return onHeadersLine(session, line, next);
        }
        
        return onBodyLine(session, line, next);
    }
    
    /**
     * Gets called when the separating line is received. This is the CLRF sequence. 
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, byte[])} but subclasses should override it if needed.
     *
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onSeparatorLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
    
    /**
     * Gets called for each received line until the CRLF sequence was received.
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, byte[])} but subclasses should override it if needed.
     * 
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onHeadersLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
    
    /**
     * Gets called for each received line after the CRLF sequence was received.
     * 
     * This implementation just calls {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, byte[])} but subclasses should override it if needed.
     * 
     * @param session
     * @param line
     * @param next
     * @return response
     */
    protected Response onBodyLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        return next.onLine(session, line);
    }
}
