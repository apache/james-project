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
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.MoveRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

public class MoveProcessor extends AbstractMessageRangeProcessor<MoveRequest> implements CapabilityImplementingProcessor {

    private final boolean moveCapabilitySupported;

    public MoveProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(MoveRequest.class, next, mailboxManager, factory, metricFactory);
        moveCapabilitySupported = mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move);
    }

    @Override
    protected List<MessageRange> process(MailboxPath targetMailbox, SelectedMailbox currentMailbox,
                                         MailboxSession mailboxSession, MessageRange messageSet) throws MailboxException {
        return getMailboxManager().moveMessages(messageSet, currentMailbox.getPath(), targetMailbox, mailboxSession);
    }

    @Override
    protected String getOperationName() {
        return "Move";
    }

    @Override
    public List<String> getImplementedCapabilities(ImapSession session) {
        if (moveCapabilitySupported) {
            return ImmutableList.of(ImapConstants.MOVE_COMMAND_NAME);
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    protected Closeable addContextToMDC(MoveRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "MOVE")
            .addContext("targetMailbox", request.getMailboxName())
            .addContext("uidEnabled", request.isUseUids())
            .addContext("idSet", IdRange.toString(request.getIdSet()))
            .build();
    }

}
