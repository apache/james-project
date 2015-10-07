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

package org.apache.james.imap.main;

import java.io.IOException;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapResponseComposer;

public class ResponseEncoder implements Responder {
    private final ImapEncoder encoder;
    private final ImapSession session;
    private final ImapResponseComposer composer;

    private IOException failure;

    public ResponseEncoder(final ImapEncoder encoder, final ImapResponseComposer composer, final ImapSession session) {
        super();
        this.encoder = encoder;
        this.composer = composer;
        this.session = session;
    }

    public void respond(final ImapResponseMessage message) {
        try {
            encoder.encode(message, composer, session);
        } catch (IOException failure) {
            this.failure = failure;
        }
    }

    /**
     * Gets the recorded failure.
     * 
     * @return the failure, or null when no failure has occurred
     */
    public final IOException getFailure() {
        return failure;
    }

}
