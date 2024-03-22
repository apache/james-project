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

package org.apache.james.mailbox.store;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxAnnotationManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.util.FunctionalUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class StoreMailboxAnnotationManager implements MailboxAnnotationManager {

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final StoreRightManager rightManager;
    private final int limitOfAnnotations;
    private final int limitAnnotationSize;

    @Inject
    public StoreMailboxAnnotationManager(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                         StoreRightManager rightManager) {
        this(mailboxSessionMapperFactory,
            rightManager,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
    }

    public StoreMailboxAnnotationManager(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                         StoreRightManager rightManager,
                                         int limitOfAnnotations,
                                         int limitAnnotationSize) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.rightManager = rightManager;
        this.limitOfAnnotations = limitOfAnnotations;
        this.limitAnnotationSize = limitAnnotationSize;
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(getAllAnnotationsReactive(mailboxPath, session).collectList());
    }

    @Override
    public Flux<MailboxAnnotation> getAllAnnotationsReactive(MailboxPath mailboxPath, MailboxSession session) {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        return checkThenGetMailboxId(mailboxPath, session)
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))
            .flatMapMany(mailboxId -> annotationMapper.executeReactive(Flux.from(annotationMapper.getAllAnnotationsReactive(mailboxId))
                    .collectList())
                .flatMapIterable(Function.identity()));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, final Set<MailboxAnnotationKey> keys)
        throws MailboxException {
        return MailboxReactorUtils.block(getAnnotationsByKeysReactive(mailboxPath, session, keys).collectList());
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);

        return checkThenGetMailboxId(mailboxPath, session)
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))
            .flatMapMany(mailboxId -> annotationMapper.executeReactive(Flux.from(annotationMapper.getAnnotationsByKeysReactive(mailboxId, keys))
                    .collectList())
                .flatMapIterable(Function.identity()));
    }

    @Override
    public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations)
        throws MailboxException {
        MailboxReactorUtils.block(updateAnnotationsReactive(mailboxPath, session, mailboxAnnotations));
    }

    @Override
    public Mono<Void> updateAnnotationsReactive(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations) {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        return annotationMapper.executeReactive(checkThenGetMailboxId(mailboxPath, session)
                .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))
            .flatMapMany(mailboxId -> Flux.fromIterable(mailboxAnnotations)
                .concatMap(annotation -> {
                    if (annotation.isNil()) {
                        return Mono.from(annotationMapper.deleteAnnotationReactive(mailboxId, annotation.getKey()));
                    }
                    return canInsertOrUpdate(mailboxId, annotation, annotationMapper)
                        .filter(FunctionalUtils.identityPredicate())
                        .flatMap(can -> Mono.from(annotationMapper.insertAnnotationReactive(mailboxId, annotation)));
                }))
            .then());
    }

    private Mono<Boolean> canInsertOrUpdate(MailboxId mailboxId, MailboxAnnotation annotation, AnnotationMapper annotationMapper) {
        return Mono.just(annotation.size() > limitAnnotationSize)
            .filter(FunctionalUtils.identityPredicate())
            .flatMap(limited -> Mono.<Boolean>error(new AnnotationException("annotation too big.")))
            .switchIfEmpty(annotationCountCanInsertOrUpdate(mailboxId, annotation, annotationMapper));
    }

    private Mono<Boolean> annotationCountCanInsertOrUpdate(MailboxId mailboxId, MailboxAnnotation annotation, AnnotationMapper annotationMapper) {
        return Mono.from(annotationMapper.existReactive(mailboxId, annotation))
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exist -> Mono.from(annotationMapper.countAnnotationsReactive(mailboxId))
                .filter(count -> count >= limitOfAnnotations)
                .flatMap(limited -> Mono.<Boolean>error(new AnnotationException("too many annotations."))))
            .switchIfEmpty(Mono.just(true));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
                                                                    Set<MailboxAnnotationKey> keys) throws MailboxException {
        return MailboxReactorUtils.block(getAnnotationsByKeysWithOneDepthReactive(mailboxPath, session, keys).collectList());
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithOneDepthReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        return checkThenGetMailboxId(mailboxPath, session)
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))
            .flatMapMany(mailboxId -> annotationMapper.executeReactive(Flux.from(annotationMapper.getAnnotationsByKeysWithOneDepthReactive(mailboxId, keys))
                    .collectList())
                .flatMapIterable(Function.identity()));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
                                                                    Set<MailboxAnnotationKey> keys) throws MailboxException {
        return MailboxReactorUtils.block(getAnnotationsByKeysWithAllDepthReactive(mailboxPath, session, keys).collectList());
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithAllDepthReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        return checkThenGetMailboxId(mailboxPath, session)
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailboxPath)))
            .flatMapMany(mailboxId -> annotationMapper.executeReactive(Flux.from(annotationMapper.getAnnotationsByKeysWithAllDepthReactive(mailboxId, keys))
                    .collectList())
                .flatMapIterable(Function.identity()));
    }

    private Mono<MailboxId> checkThenGetMailboxId(MailboxPath mailboxPath, MailboxSession session) {
        return mailboxSessionMapperFactory.getMailboxMapper(session).findMailboxByPath(mailboxPath)
            .flatMap(mailbox -> Mono.from(rightManager.hasRightReactive(mailboxPath, Right.Read, session))
                .filter(FunctionalUtils.identityPredicate().negate())
                .flatMap(hasRight -> Mono.<MailboxId>error(new InsufficientRightsException("Not enough rights on " + mailboxPath)))
                .switchIfEmpty(Mono.just(mailbox.getMailboxId())));
    }
}
