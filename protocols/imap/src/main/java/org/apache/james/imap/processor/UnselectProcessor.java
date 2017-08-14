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
package org.apache.james.imap.processor;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.UnselectRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

/**
 * Processor which implements the UNSELECT extension.
 * 
 * See RFC3691
 */
public class UnselectProcessor extends AbstractMailboxProcessor<UnselectRequest> implements CapabilityImplementingProcessor {

    private final static List<String> UNSELECT = Collections.unmodifiableList(Arrays.asList("UNSELECT"));

    public UnselectProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(UnselectRequest.class, next, mailboxManager, factory, metricFactory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(UnselectRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {
        if (session.getSelected() != null) {
            session.deselect();
            okComplete(command, tag, responder);
        } else {
            taggedBad(command, tag, responder, HumanReadableText.UNSELECT);
        }

    }

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return UNSELECT;
    }

    @Override
    protected Closeable addContextToMDC(UnselectRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "UNSELECT")
            .build();
    }
}
