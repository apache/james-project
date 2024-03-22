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

import static org.apache.james.util.ReactorUtils.logOnError;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.GetMetadataRequest;
import org.apache.james.imap.message.response.MetadataResponse;
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

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Support for RFC-5464 IMAP METADATA (GETMETADATA command)
 *
 * CF https://www.rfc-editor.org/rfc/rfc5464.html
 */
public class GetMetadataProcessor extends AbstractMailboxProcessor<GetMetadataRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetMetadataProcessor.class);
    private final ImmutableList<Capability> capabilities;

    @Inject
    public GetMetadataProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                MetricFactory metricFactory) {
        super(GetMetadataRequest.class, mailboxManager, factory, metricFactory);
        this.capabilities = computeCapabilities();
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return capabilities;
    }

    private ImmutableList<Capability> computeCapabilities() {
        return Optional.ofNullable(getMailboxManager().getSupportedMailboxCapabilities())
            .map(capabilities -> capabilities.contains(MailboxManager.MailboxCapabilities.Annotation))
            .map(annotationCap -> ImmutableList.of(ImapConstants.SUPPORTS_ANNOTATION))
            .orElseGet(ImmutableList::of);
    }

    @Override
    protected Mono<Void> processRequestReactive(GetMetadataRequest request, ImapSession session, Responder responder) {
        String mailboxName = request.getMailboxName();
        Optional<Integer> maxsize = request.getMaxsize();

        return getMailboxAnnotations(session, request.getKeys(), request.getDepth(), PathConverter.forSession(session).buildFullPath(mailboxName))
            .collectList()
            .flatMap(mailboxAnnotations -> Mono.fromCallable(() -> getMaxSizeValue(mailboxAnnotations, maxsize))
                .flatMap(maximumOversizedSize -> Mono.fromRunnable(() -> respond(request, responder, mailboxName, mailboxAnnotations, maxsize, maximumOversizedSize)))
                .then())
            .doOnEach(logOnError(MailboxNotFoundException.class,
                e -> LOGGER.info("The command: {} is failed because not found mailbox {}", request.getCommand().getName(), request.getMailboxName())))
            .onErrorResume(MailboxNotFoundException.class,
                error -> Mono.fromRunnable(() -> no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, ResponseCode.tryCreate())))
            .doOnEach(logOnError(MailboxException.class,
                e -> LOGGER.error("GetAnnotation on mailbox {} failed for user {}", request.getMailboxName(), session.getUserName(), e)))
            .onErrorResume(MailboxException.class,
                error -> Mono.fromRunnable(() -> no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING)));
    }

    private void respond(ImapRequest request, Responder responder, String mailboxName,
                         List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize, Optional<Integer> maximumOversizedSize) {
        if (maximumOversizedSize.isPresent()) {
            responder.respond(new MetadataResponse(mailboxName, filterItemsBySize(mailboxAnnotations, maxsize)));
            okComplete(request, ResponseCode.longestMetadataEntry(maximumOversizedSize.get()), responder);
        } else {
            responder.respond(new MetadataResponse(mailboxName, mailboxAnnotations));
            okComplete(request, responder);
        }
    }

    private Optional<Integer> getMaxSizeValue(List<MailboxAnnotation> mailboxAnnotations, Optional<Integer> maxsize) {
        return maxsize.flatMap(value -> mailboxAnnotations.stream()
            .map(MailboxAnnotation::size)
            .filter(size -> size > value)
            .reduce(Integer::max));
    }

    private List<MailboxAnnotation> filterItemsBySize(List<MailboxAnnotation> mailboxAnnotations, final Optional<Integer> maxsize) {
        Predicate<MailboxAnnotation> lowerPredicate = annotation -> maxsize
            .map(maxSizeInput -> (annotation.size() <= maxSizeInput))
            .orElse(true);

        return mailboxAnnotations.stream()
            .filter(lowerPredicate)
            .collect(ImmutableList.toImmutableList());
    }

    private Flux<MailboxAnnotation> getMailboxAnnotations(ImapSession session, Set<MailboxAnnotationKey> keys, GetMetadataRequest.Depth depth, MailboxPath mailboxPath) {
        MailboxSession mailboxSession = session.getMailboxSession();
        switch (depth) {
            case ZERO:
                return getMailboxAnnotationsWithDepthZero(keys, mailboxPath, mailboxSession);
            case ONE:
                return Flux.from(getMailboxManager().getAnnotationsByKeysWithOneDepthReactive(mailboxPath, mailboxSession, keys));
            case INFINITY:
                return Flux.from(getMailboxManager().getAnnotationsByKeysWithAllDepthReactive(mailboxPath, mailboxSession, keys));
            default:
                return Flux.error(new NotImplementedException("Not implemented"));
        }
    }

    private Flux<MailboxAnnotation> getMailboxAnnotationsWithDepthZero(Set<MailboxAnnotationKey> keys, MailboxPath mailboxPath, MailboxSession mailboxSession) {
        if (keys.isEmpty()) {
            return Flux.from(getMailboxManager().getAllAnnotationsReactive(mailboxPath, mailboxSession));
        } else {
            return Flux.from(getMailboxManager().getAnnotationsByKeysReactive(mailboxPath, mailboxSession, keys));
        }
    }

    @Override
    protected MDCBuilder mdc(GetMetadataRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_ANNOTATION")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("depth", request.getDepth().getCode())
            .addToContextIfPresent("maxSize", request.getMaxsize()
                .map(i -> Integer.toString(i)))
            .addToContext("keys", request.getKeys().toString());
    }
}
