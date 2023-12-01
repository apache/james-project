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

package org.apache.james.mailbox.postgres.mail;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxAnnotationDAO;
import org.apache.james.mailbox.store.mail.AnnotationMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresAnnotationMapper implements AnnotationMapper {
    private final PostgresMailboxAnnotationDAO annotationDAO;

    @Inject
    public PostgresAnnotationMapper(PostgresMailboxAnnotationDAO annotationDAO) {
        this.annotationDAO = annotationDAO;
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId) {
        return getAllAnnotationsReactive(mailboxId)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAllAnnotationsReactive(MailboxId mailboxId) {
        return annotationDAO.getAllAnnotations((PostgresMailboxId) mailboxId);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysReactive(mailboxId, keys)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return annotationDAO.getAnnotationsByKeys((PostgresMailboxId) mailboxId, keys);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysWithOneDepthReactive(mailboxId, keys)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithOneDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(keys).flatMap(mailboxAnnotationKey ->
            annotationDAO.getAnnotationsByKeyLike((PostgresMailboxId) mailboxId, mailboxAnnotationKey)
                .filter(annotation -> mailboxAnnotationKey.isParentOrIsEqual(annotation.getKey())));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysWithAllDepthReactive(mailboxId, keys)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithAllDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(keys).flatMap(mailboxAnnotationKey ->
            annotationDAO.getAnnotationsByKeyLike((PostgresMailboxId) mailboxId, mailboxAnnotationKey)
                .filter(annotation -> mailboxAnnotationKey.isAncestorOrIsEqual(annotation.getKey())));
    }

    @Override
    public void deleteAnnotation(MailboxId mailboxId, MailboxAnnotationKey key) {
        deleteAnnotationReactive(mailboxId, key)
            .block();
    }

    @Override
    public Mono<Void> deleteAnnotationReactive(MailboxId mailboxId, MailboxAnnotationKey key) {
        return annotationDAO.deleteAnnotation((PostgresMailboxId) mailboxId, key);
    }

    @Override
    public void insertAnnotation(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        insertAnnotationReactive(mailboxId, mailboxAnnotation)
            .block();
    }

    @Override
    public Mono<Void> insertAnnotationReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return annotationDAO.insertAnnotation((PostgresMailboxId) mailboxId, mailboxAnnotation);
    }

    @Override
    public boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return existReactive(mailboxId, mailboxAnnotation)
            .block();
    }

    @Override
    public Mono<Boolean> existReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return annotationDAO.exist((PostgresMailboxId) mailboxId, mailboxAnnotation.getKey());
    }

    @Override
    public int countAnnotations(MailboxId mailboxId) {
        return countAnnotationsReactive(mailboxId)
            .block();
    }

    @Override
    public Mono<Integer> countAnnotationsReactive(MailboxId mailboxId) {
        return annotationDAO.countAnnotations((PostgresMailboxId) mailboxId);
    }
}
