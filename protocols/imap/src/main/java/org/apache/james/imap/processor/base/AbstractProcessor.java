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

import java.util.List;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public abstract class AbstractProcessor<M extends ImapMessage> implements ImapProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessor.class);

    private final Class<M> acceptableClass;

    public AbstractProcessor(Class<M> acceptableClass) {
        this.acceptableClass = acceptableClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> processReactive(ImapMessage message, Responder responder, ImapSession session) {
        M acceptableMessage = (M) message;
        if (LOGGER.isDebugEnabled()) {
            return doProcess(acceptableMessage, responder, session)
                .contextWrite(ReactorUtils.context("imap-processor", mdc(acceptableMessage)));
        }
        return initialLog(message)
            .then(doProcess(acceptableMessage, responder, session))
            .contextWrite(ReactorUtils.context("imap-processor", mdc(acceptableMessage)));
    }

    private Mono<Void> initialLog(ImapMessage message) {
        if (LOGGER.isDebugEnabled()) {
            return ReactorUtils.logAsMono(() -> LOGGER.debug("Processing {}", message.toString()));
        }
        return Mono.empty();
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {

    }

    protected Class<M> acceptableClass() {
        return acceptableClass;
    }

    public List<Class<? extends M>> acceptableClasses() {
        return ImmutableList.of(acceptableClass);
    }

    /**
     * Processes an acceptable message. Errors are managed.
     * 
     * @param acceptableMessage
     *            <code>M</code>, not null
     * @param responder
     *            <code>Responder</code>, not null
     * @param session
     *            <code>ImapSession</code>, not null
     */
    protected abstract Mono<Void> doProcess(M acceptableMessage, Responder responder, ImapSession session);

    /**
     * Add request specific information to the MDC, for contextual logging
     */
    protected abstract MDCBuilder mdc(M message);
}
