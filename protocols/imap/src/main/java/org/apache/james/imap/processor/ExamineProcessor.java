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

import org.apache.james.events.EventBus;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.message.request.ExamineRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

public class ExamineProcessor extends AbstractSelectionProcessor<ExamineRequest> {

    public ExamineProcessor(ImapProcessor next, MailboxManager mailboxManager, EventBus eventBus, StatusResponseFactory statusResponseFactory,
                            MetricFactory metricFactory) {
        super(ExamineRequest.class, next, mailboxManager, statusResponseFactory, true, metricFactory, eventBus);
    }

    @Override
    protected Closeable addContextToMDC(ExamineRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "EXAMINE")
            .addContext("mailbox", request.getMailboxName())
            .addContext("condstore", Boolean.toString(request.getCondstore()))
            .addContext("knownModseq", request.getKnownModSeq())
            .addContext("knownUids", UidRange.toString(request.getKnownUidSet()))
            .addContext("knownIdRange", IdRange.toString(request.getKnownSequenceSet()))
            .addContext("lastKnownUidValidity", request.getLastKnownUidValidity())
            .addContext("uidSet", UidRange.toString(request.getUidSet()))
            .build();
    }
}
