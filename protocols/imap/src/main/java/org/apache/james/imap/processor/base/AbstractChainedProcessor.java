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

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;

abstract public class AbstractChainedProcessor<M extends ImapMessage> implements ImapProcessor {

    private final ImapProcessor next;
    private Class<M> acceptableClass;

    /**
     * Constructs a chainable <code>ImapProcessor</code>.
     * 
     * @param next
     *            next <code>ImapProcessor</code> in the chain, not null
     */
    public AbstractChainedProcessor(Class<M> acceptableClass, final ImapProcessor next) {
        this.next = next;
        this.acceptableClass = acceptableClass;
    }

    /**
     * @see
     * org.apache.james.imap.api.process.ImapProcessor#process(org.apache.james.imap.api.ImapMessage,
     * org.apache.james.imap.api.process.ImapProcessor.Responder,
     * org.apache.james.imap.api.process.ImapSession)
     */
    @SuppressWarnings("unchecked")
    public void process(ImapMessage message, Responder responder, ImapSession session) {
        final boolean isAcceptable = isAcceptable(message);
        if (isAcceptable) {
            doProcess((M) message, responder, session);
        } else {
            next.process(message, responder, session);
        }
    }

    /**
     * Is the given message acceptable?
     * 
     * @param message
     *            <code>ImapMessage</code>, not null
     * @return true if the given message is processable by this processable
     */
    protected boolean isAcceptable(final ImapMessage message) {
        return acceptableClass.isInstance(message);
    }

    /**
     * Processes an acceptable message. Only messages passing
     * {@link #isAcceptable(ImapMessage)} should be passed to this method.
     * 
     * @param acceptableMessage
     *            <code>M</code>, not null
     * @param responder
     *            <code>Responder</code>, not null
     * @param session
     *            <code>ImapSession</code>, not null
     */
    abstract protected void doProcess(final M acceptableMessage, final Responder responder, final ImapSession session);
}
