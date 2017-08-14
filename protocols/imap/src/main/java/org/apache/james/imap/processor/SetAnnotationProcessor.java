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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.SetAnnotationRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

public class SetAnnotationProcessor extends AbstractMailboxProcessor<SetAnnotationRequest> implements CapabilityImplementingProcessor {

    public SetAnnotationProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(SetAnnotationRequest.class, next, mailboxManager, factory, metricFactory);
    }

    public List<String> getImplementedCapabilities(ImapSession session) {
        return ImmutableList.of(ImapConstants.SUPPORTS_ANNOTATION);
    }

    protected void doProcess(SetAnnotationRequest message, ImapSession session, String tag, ImapCommand command,
            Responder responder) {
        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        final String mailboxName = message.getMailboxName();
        try {
            MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

            mailboxManager.updateAnnotations(mailboxPath, mailboxSession, message.getMailboxAnnotations());

            okComplete(command, tag, responder);
        } catch (MailboxNotFoundException e) {
            session.getLog().info(command.getName() + " failed for mailbox " + mailboxName, e);
            no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
        } catch (AnnotationException e) {
            session.getLog().info(command.getName() + " failed for mailbox " + mailboxName, e);
            no(command, tag, responder, new HumanReadableText(HumanReadableText.MAILBOX_ANNOTATION_KEY, e.getMessage()));
        } catch (MailboxException e) {
            session.getLog().error(command.getName() + " failed for mailbox " + mailboxName, e);
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected Closeable addContextToMDC(SetAnnotationRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SET_ANNOTATION")
            .addContext("mailbox", message.getMailboxName())
            .addContext("annotations", message.getMailboxAnnotations())
            .build();
    }
}
