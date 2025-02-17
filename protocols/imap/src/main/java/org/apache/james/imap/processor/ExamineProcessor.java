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

import java.util.Objects;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.events.EventBus;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ExamineRequest;
import org.apache.james.mailbox.MailboxCounterCorrector;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

public class ExamineProcessor extends AbstractSelectionProcessor<ExamineRequest> {

    @Inject
    public ExamineProcessor(MailboxManager mailboxManager, EventBus eventBus, StatusResponseFactory statusResponseFactory,
                            MetricFactory metricFactory, PathConverter.Factory pathConverterFactory, MailboxCounterCorrector mailboxCounterCorrector) {
        super(ExamineRequest.class, mailboxManager, statusResponseFactory, pathConverterFactory, true, metricFactory, eventBus, mailboxCounterCorrector);
    }

    @Override
    protected MDCBuilder mdc(ExamineRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "EXAMINE")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("condstore", Boolean.toString(request.getCondstore()))
            .addToContextIfPresent("knownModseq", Optional.ofNullable(request.getKnownModSeq()).map(Objects::toString))
            .addToContext("knownUids", UidRange.toString(request.getKnownUidSet()))
            .addToContext("knownIdRange", IdRange.toString(request.getKnownSequenceSet()))
            .addToContext("lastKnownUidValidity", request.getLastKnownUidValidity().toString())
            .addToContext("uidSet", UidRange.toString(request.getUidSet()));
    }
}
