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
import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

/**
 * SETQUOTA processor
 */
public class SetQuotaProcessor extends AbstractMailboxProcessor<SetQuotaRequest> implements CapabilityImplementingProcessor {
    private static final List<String> CAPABILITIES = Collections.singletonList(ImapConstants.SUPPORTS_QUOTA);

    @Override
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    public SetQuotaProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(SetQuotaRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void doProcess(SetQuotaRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {
        Object[] params = new Object[]{
            "Full admin rights",
            command.getName(),
            "Can not perform SETQUOTA commands"
        };
        HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
        no(command, tag, responder, humanReadableText);
    }

    @Override
    protected Closeable addContextToMDC(SetQuotaRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SET_QUOTA")
            .addContext("quotaRoot", message.getQuotaRoot())
            .addContext("limits", message.getResourceLimits())
            .build();
    }
}
