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

package org.apache.james.imap.processor.base;

import java.io.Closeable;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.util.MDCBuilder;

public class ImapResponseMessageProcessor extends AbstractChainedProcessor<ImapResponseMessage> {

    public ImapResponseMessageProcessor(ImapProcessor next) {
        super(ImapResponseMessage.class, next);
    }

    @Override
    protected void doProcess(ImapResponseMessage acceptableMessage, Responder responder, ImapSession session) {
        responder.respond(acceptableMessage);
    }

    @Override
    protected Closeable addContextToMDC(ImapResponseMessage message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "RESPOND")
            .build();
    }
}
