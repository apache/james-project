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

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.GetAnnotationRequest;
import org.apache.james.imap.message.response.AnnotationResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

public class GetAnnotationProcessor extends AbstractMailboxProcessor<GetAnnotationRequest> implements CapabilityImplementingProcessor {
    public GetAnnotationProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(GetAnnotationRequest.class, next, mailboxManager, factory, metricFactory);
    }

    public List<String> getImplementedCapabilities(ImapSession session) {
        return ImmutableList.of(ImapConstants.SUPPORTS_ANNOTATION);
    }

    protected void doProcess(GetAnnotationRequest message, ImapSession session, String tag, ImapCommand command,
            Responder responder) {
        try {
            proceed(message, session, tag, command, responder);
        } catch (MailboxNotFoundException e) {
            session.getLog().info("The command: {} is failed because not found mailbox {}", command.getName(), message.getMailboxName());
            no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, ResponseCode.tryCreate());
        } catch (MailboxException e) {
            session.getLog().error("GetAnnotation on mailbox " + message.getMailboxName() + " failed for user " + ImapSessionUtils.getUserName(session), e);
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private void proceed(GetAnnotationRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) throws MailboxException {
        String mailboxName = message.getMailboxName();
        Optional<Integer> maxsize = message.getMaxsize();
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

        List<MailboxAnnotation> mailboxAnnotations = getMailboxAnnotations(session, message.getKeys(), message.getDepth(), mailboxPath);
        Optional<Integer> maximumOversizedSize = getMaxSizeValue(mailboxAnnotations, maxsize);

        respond(tag, command, responder, mailboxName, mailboxAnnotations, maxsize, maximumOversizedSize);
    }

    private void respond(String tag, ImapCommand command, Responder responder, String mailboxName,
                         List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize, Optional<Integer> maximumOversizedSize) {
        if (maximumOversizedSize.isPresent()) {
            responder.respond(new AnnotationResponse(mailboxName, filterItemsBySize(responder, mailboxName, mailboxAnnotations, maxsize)));
            okComplete(command, tag, ResponseCode.longestMetadataEntry(maximumOversizedSize.get()), responder);
        } else {
            responder.respond(new AnnotationResponse(mailboxName, mailboxAnnotations));
            okComplete(command, tag, responder);
        }
    }

    private Optional<Integer> getMaxSizeValue(final List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize) {
        if (maxsize.isPresent()) {
            return maxsize.transform(value -> getMaxSizeOfOversizedItems(mailboxAnnotations, value)).get();
        }
        return Optional.absent();
    }

    private List<MailboxAnnotation> filterItemsBySize(Responder responder, String mailboxName, List<MailboxAnnotation> mailboxAnnotations, final Optional<Integer> maxsize) {
        Predicate<MailboxAnnotation> lowerPredicate = annotation -> maxsize
            .transform(maxSizeInput -> (annotation.size() <= maxSizeInput))
            .or(true);

        return FluentIterable.from(mailboxAnnotations).filter(lowerPredicate).toList();
    }

    private List<MailboxAnnotation> getMailboxAnnotations(ImapSession session, Set<MailboxAnnotationKey> keys, GetAnnotationRequest.Depth depth, MailboxPath mailboxPath) throws MailboxException {
        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        switch (depth) {
            case ZERO:
                return getMailboxAnnotationsWithDepthZero(keys, mailboxPath, mailboxSession);
            case ONE:
                return getMailboxManager().getAnnotationsByKeysWithOneDepth(mailboxPath, mailboxSession, keys);
            case INFINITY:
                return getMailboxManager().getAnnotationsByKeysWithAllDepth(mailboxPath, mailboxSession, keys);
            default:
                throw new NotImplementedException();
        }
    }

    private List<MailboxAnnotation> getMailboxAnnotationsWithDepthZero(Set<MailboxAnnotationKey> keys, MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        if (keys.isEmpty()) {
            return getMailboxManager().getAllAnnotations(mailboxPath, mailboxSession);
        } else {
            return getMailboxManager().getAnnotationsByKeys(mailboxPath, mailboxSession, keys);
        }
    }

    private Optional<Integer> getMaxSizeOfOversizedItems(List<MailboxAnnotation> mailboxAnnotations, final Integer maxsize) {
        Predicate<MailboxAnnotation> filterOverSizedAnnotation = annotation -> annotation.size() > maxsize;

        ImmutableSortedSet<Integer> overLimitSizes = FluentIterable.from(mailboxAnnotations)
            .filter(filterOverSizedAnnotation)
            .transform(MailboxAnnotation::size)
            .toSortedSet(Comparator.reverseOrder());

        if (overLimitSizes.isEmpty()) {
            return Optional.absent();
        }
        return Optional.of(overLimitSizes.first());
    }

}
