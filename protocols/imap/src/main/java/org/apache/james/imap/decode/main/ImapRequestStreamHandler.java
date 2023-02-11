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

package org.apache.james.imap.decode.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.main.AbstractImapRequestHandler;
import org.apache.james.imap.message.request.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public final class ImapRequestStreamHandler extends AbstractImapRequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapRequestStreamHandler.class);

    public ImapRequestStreamHandler(ImapDecoder decoder, ImapProcessor processor, ImapEncoder encoder) {
        super(decoder, processor, encoder);
    }

    /**
     * This method parses IMAP commands read off the wire in handleConnection.
     * Actual processing of the command (possibly including additional back and
     * forth communication with the client) is delegated to one of a number of
     * command specific handler methods. The primary purpose of this method is
     * to parse the raw command string to determine exactly which handler should
     * be called. It returns true if expecting additional commands, false
     * otherwise.
     * 
     * @return whether additional commands are expected.
     */
    public boolean handleRequest(InputStream input, OutputStream output, ImapSession session) {
        final boolean result;
        if (isSelectedMailboxDeleted(session)) {
            writeSignoff(output, session);
            result = false;
        } else {
            ImapRequestLineReader request = new ImapRequestStreamLineReader(input, output);

            try {
                request.nextChar();
            } catch (DecodingException e) {
                LOGGER.debug("Unexpected end of line. Cannot handle request: ", e);
                abandon(output, session);
                return false;
            }

            OutputStreamImapResponseWriter writer = new OutputStreamImapResponseWriter(output);
            ImapResponseComposerImpl response = new ImapResponseComposerImpl(writer);
            writer.setFlushCallback(response::flush);

            if (doProcessRequest(request, response, session)) {

                try {
                    // Consume the rest of the line, throwing away any extras.
                    // This allows us to clean up after a protocol error.
                    request.consumeLine();
                } catch (DecodingException e) {
                    // Cannot clean up. No recovery is therefore possible.
                    // Abandon connection.
                    LOGGER.info("Fault during clean up: {}", e.getMessage());
                    LOGGER.debug("Abandoning after fault in clean up", e);
                    abandon(output, session);
                    return false;
                }

                result = !(ImapSessionState.LOGOUT == session.getState());
            } else {
                LOGGER.debug("Connection was abandoned after request processing failed.");
                result = false;
                abandon(output, session);
            }
            try {
                response.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private void writeSignoff(OutputStream output, ImapSession session) {
        try {
            output.write(MAILBOX_DELETED_SIGNOFF);
        } catch (IOException e) {
            LOGGER.warn("Failed to write signoff");
            LOGGER.debug("Failed to write signoff:", e);
        }
    }

    private void abandon(OutputStream out, ImapSession session) {
        if (session != null) {
            try {
                session.logout().block();
            } catch (Throwable t) {
                LOGGER.warn("Session logout failed. Resources may not be correctly recycled.");
            }
        }
        try {
            out.write(ABANDON_SIGNOFF);
        } catch (Throwable t) {
            LOGGER.debug("Failed to write ABANDON_SIGNOFF", t);
        }
        processor.process(SystemMessage.FORCE_LOGOUT, new SilentResponder(), session);
    }

}
