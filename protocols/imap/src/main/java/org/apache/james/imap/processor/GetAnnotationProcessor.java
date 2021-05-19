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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
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
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

public class GetAnnotationProcessor extends AbstractMailboxProcessor<GetAnnotationRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetAnnotationProcessor.class);

    public GetAnnotationProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(GetAnnotationRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return ImmutableList.of(ImapConstants.SUPPORTS_ANNOTATION);
    }

    @Override
    protected void processRequest(GetAnnotationRequest request, ImapSession session, Responder responder) {
        try {
            proceed(request, session, responder);
        } catch (MailboxNotFoundException e) {
            LOGGER.info("The command: {} is failed because not found mailbox {}", request.getCommand().getName(), request.getMailboxName());
            no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, ResponseCode.tryCreate());
        } catch (MailboxException e) {
            LOGGER.error("GetAnnotation on mailbox {} failed for user {}", request.getMailboxName(), session.getUserName(), e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private void proceed(GetAnnotationRequest message, ImapSession session, Responder responder) throws MailboxException {
        String mailboxName = message.getMailboxName();
        Optional<Integer> maxsize = message.getMaxsize();
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

        List<MailboxAnnotation> mailboxAnnotations = getMailboxAnnotations(session, message.getKeys(), message.getDepth(), mailboxPath);
        Optional<Integer> maximumOversizedSize = getMaxSizeValue(mailboxAnnotations, maxsize);

        respond(message, responder, mailboxName, mailboxAnnotations, maxsize, maximumOversizedSize);
    }

    private void respond(ImapRequest request, Responder responder, String mailboxName,
                         List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize, Optional<Integer> maximumOversizedSize) {
        if (maximumOversizedSize.isPresent()) {
            responder.respond(new AnnotationResponse(mailboxName, filterItemsBySize(mailboxAnnotations, maxsize)));
            okComplete(request, ResponseCode.longestMetadataEntry(maximumOversizedSize.get()), responder);
        } else {
            responder.respond(new AnnotationResponse(mailboxName, mailboxAnnotations));
            okComplete(request, responder);
        }
    }

    private Optional<Integer> getMaxSizeValue(final List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize) {
        if (maxsize.isPresent()) {
            return maxsize.map(value -> getMaxSizeOfOversizedItems(mailboxAnnotations, value)).get();
        }
        return Optional.empty();
    }

    private List<MailboxAnnotation> filterItemsBySize(List<MailboxAnnotation> mailboxAnnotations, final Optional<Integer> maxsize) {
        Predicate<MailboxAnnotation> lowerPredicate = annotation -> maxsize
            .map(maxSizeInput -> (annotation.size() <= maxSizeInput))
            .orElse(true);

        return mailboxAnnotations.stream()
            .filter(lowerPredicate)
            .collect(Guavate.toImmutableList());
    }

    private List<MailboxAnnotation> getMailboxAnnotations(ImapSession session, Set<MailboxAnnotationKey> keys, GetAnnotationRequest.Depth depth, MailboxPath mailboxPath) throws MailboxException {
        MailboxSession mailboxSession = session.getMailboxSession();
        switch (depth) {
            case ZERO:
                return getMailboxAnnotationsWithDepthZero(keys, mailboxPath, mailboxSession);
            case ONE:
                return getMailboxManager().getAnnotationsByKeysWithOneDepth(mailboxPath, mailboxSession, keys);
            case INFINITY:
                return getMailboxManager().getAnnotationsByKeysWithAllDepth(mailboxPath, mailboxSession, keys);
            default:
                throw new NotImplementedException("Not implemented");
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

        ImmutableSortedSet<Integer> overLimitSizes = mailboxAnnotations.stream()
            .filter(filterOverSizedAnnotation)
            .map(MailboxAnnotation::size)
            .collect(Guavate.toImmutableSortedSet(Comparator.reverseOrder()));

        if (overLimitSizes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(overLimitSizes.first());
    }

    @Override
    protected Closeable addContextToMDC(GetAnnotationRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_ANNOTATION")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("depth", request.getDepth().getCode())
            .addToContextIfPresent("maxSize", request.getMaxsize()
                .map(i -> Integer.toString(i)))
            .addToContext("keys", request.getKeys().toString())
            .build();
    }
}
