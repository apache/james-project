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

import java.util.List;

import javax.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.SetMetadataRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Support for RFC-5464 IMAP METADATA (SETMETADATA command)
 *
 * CF https://www.rfc-editor.org/rfc/rfc5464.html
 */
public class SetMetadataProcessor extends AbstractMailboxProcessor<SetMetadataRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMetadataProcessor.class);
    private final ImmutableList<Capability> capabilities;

    @Inject
    public SetMetadataProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                MetricFactory metricFactory) {
        super(SetMetadataRequest.class, mailboxManager, factory, metricFactory);
        this.capabilities = computeCapabilities();
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return capabilities;
    }

    private ImmutableList<Capability> computeCapabilities() {
        if (getMailboxManager().getSupportedMailboxCapabilities().contains(MailboxManager.MailboxCapabilities.Annotation)) {
            return ImmutableList.of(ImapConstants.SUPPORTS_ANNOTATION);
        }
        return ImmutableList.of();
    }

    @Override
    protected void processRequest(SetMetadataRequest request, ImapSession session, Responder responder) {
        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = session.getMailboxSession();
        final String mailboxName = request.getMailboxName();
        try {
            MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

            mailboxManager.updateAnnotations(mailboxPath, mailboxSession, request.getMailboxAnnotations());

            okComplete(request, responder);
        } catch (MailboxNotFoundException e) {
            LOGGER.info("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e);
            no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
        } catch (AnnotationException e) {
            LOGGER.info("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e);
            no(request, responder, new HumanReadableText(HumanReadableText.MAILBOX_ANNOTATION_KEY, e.getMessage()));
        } catch (MailboxException e) {
            LOGGER.error("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected MDCBuilder mdc(SetMetadataRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "SET_ANNOTATION")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("annotations", request.getMailboxAnnotations().toString());
    }
}
