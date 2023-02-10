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

package org.apache.james.imap.api.process;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.response.ImapResponseMessage;

import reactor.core.publisher.Mono;

/**
 * <p>
 * Processable IMAP command.
 * </p>
 * <p>
 * <strong>Note:</strong> this is a transitional API and is liable to change.
 * </p>
 */
public interface ImapProcessor {

    /**
     * Performs processing of the command. If this processor does not understand
     * the given message then it must return an appropriate message as per the
     * specification. RuntimeException should not be thrown in this
     * circumstance.
     * 
     * @param message <code>not null</code>
     * @param responder <code>not null</code>, the responder use write response for message
     * @param session the imap session
     */
    default void process(ImapMessage message, Responder responder, ImapSession session) {
        processReactive(message, responder, session).block();
    }

    default Mono<Void> processReactive(ImapMessage message, Responder responder, ImapSession session) {
        return Mono.fromRunnable(() -> process(message, responder, session));
    }

    void configure(ImapConfiguration imapConfiguration);

    /**
     * Response message sink.
     */
    interface Responder {
        /**
         * Writes the given response.
         * 
         * @param message <code>not null</code>
         */
        void respond(ImapResponseMessage message);

        void flush();
    }
}
