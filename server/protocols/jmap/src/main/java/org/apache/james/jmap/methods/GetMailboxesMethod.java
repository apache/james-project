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

package org.apache.james.jmap.methods;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.JmapResponse.Builder;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.Mailbox;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.jmap.model.Role;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class GetMailboxesMethod<Id extends MailboxId> implements Method {
    
    private static final boolean DONT_RESET_RECENT = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethod.class);

    private final JmapRequestParser jmapRequestParser;
    private final JmapResponseWriter jmapResponseWriter;
    private final MailboxManager mailboxManager; 
    private final MailboxMapperFactory<Id> mailboxMapperFactory;

    @Inject
    @VisibleForTesting public GetMailboxesMethod(JmapRequestParser jmapRequestParser, JmapResponseWriter jmapResponseWriter, 
            MailboxManager mailboxManager, MailboxMapperFactory<Id> mailboxMapperFactory) {

        this.jmapRequestParser = jmapRequestParser;
        this.jmapResponseWriter = jmapResponseWriter;
        this.mailboxManager = mailboxManager;
        this.mailboxMapperFactory = mailboxMapperFactory;
    }

    public String methodName() {
        return "getMailboxes";
    }

    public ProtocolResponse process(AuthenticatedProtocolRequest request) {
        Builder responseBuilder = JmapResponse.forRequest(request);
        try {
            jmapRequestParser.extractJmapRequest(request, GetMailboxesRequest.class);
        } catch (IOException e) {
            if (e.getCause() instanceof NotImplementedException) {
                return jmapResponseWriter.formatErrorResponse(request, "Not yet implemented");
            } else {
                return jmapResponseWriter.formatErrorResponse(request, "invalidArguments");
            }
        }
        
        try {
            MailboxSession mailboxSession = request.getMailboxSession();
            responseBuilder.response(getMailboxesResponse(mailboxSession));
            return jmapResponseWriter.formatMethodResponse(responseBuilder.build());
        } catch (MailboxException e) {
            return jmapResponseWriter.formatErrorResponse(request);
        }
    }

    private GetMailboxesResponse getMailboxesResponse(MailboxSession mailboxSession) throws MailboxException {
        GetMailboxesResponse.Builder builder = GetMailboxesResponse.builder();

        mailboxManager.list(mailboxSession)
            .stream()
            .map(mailboxPath -> mailboxFromMailboxPath(mailboxPath, mailboxSession))
            .forEach(mailbox -> builder.add(mailbox.get()));

        return builder.build();
    }
    
    private Optional<Mailbox> mailboxFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.ofNullable(Mailbox.builder()
                    .id(getMailboxId(mailboxPath, mailboxSession))
                    .name(mailboxPath.getName())
                    .role(Role.from(mailboxPath.getName()))
                    .unreadMessages(unreadMessages(mailboxPath, mailboxSession))
                    .build());
        } catch (MailboxException e) {
            LOGGER.warn("Cannot find mailbox for :" + mailboxPath.getName(), e);
            return Optional.empty();
        }
    }

    private String getMailboxId(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException, MailboxNotFoundException {
        return mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId()
                .serialize();
    }

    private long unreadMessages(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
                .getMetaData(DONT_RESET_RECENT, mailboxSession, FetchGroup.UNSEEN_COUNT)
                .getUnseenCount();
    }

}
