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

package org.apache.james.droplists.jpa;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.droplists.jpa.model.JPADropListEntry;
import org.apache.james.sieverepository.api.exception.StorageException;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JPADropList implements DropList {

    private static final String OWNER_SCOPE = "ownerScope";
    private static final String OWNER = "owner";
    private static final String DENIED_ENTITY = "deniedEntity";

    private final TransactionRunner transactionRunner;
    private final EntityManagerFactory entityManagerFactory;

    @Inject
    public JPADropList(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
    }

    @Override
    public Mono<Void> add(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        return Mono.fromRunnable(() ->
            transactionRunner.run(entityManager ->
                entityManager.persist(JPADropListEntry.fromDropListEntry(entry))));
    }

    @Override
    public Mono<Void> remove(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        return Mono.fromRunnable(() -> transactionRunner.runAndHandleException(Throwing.consumer(entityManager ->
            entityManager.createNamedQuery("removeDropListEntry")
                .setParameter(OWNER_SCOPE, entry.getOwnerScope().name())
                .setParameter(OWNER, entry.getOwner())
                .setParameter(DENIED_ENTITY, entry.getDeniedEntity())
                .executeUpdate()
        ), throwStorageExceptionConsumer("Unable to remove denied entity " + entry.getDeniedEntity())));
    }

    @Override
    public Flux<DropListEntry> list(OwnerScope ownerScope, String owner) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        return Flux.fromStream(() -> getDropListEntries(entityManager, ownerScope, owner)
                .map(JPADropListEntry::toDropListEntry))
            .doFinally(any -> EntityManagerUtils.safelyClose(entityManager));
    }

    @Override
    public Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        Preconditions.checkArgument(sender != null);
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        return Mono.fromCallable(() -> queryDropList(entityManager, ownerScope, owner, sender))
            .doFinally(any -> EntityManagerUtils.safelyClose(entityManager));
    }

    @SuppressWarnings("unchecked")
    private Stream<JPADropListEntry> getDropListEntries(EntityManager entityManager, OwnerScope ownerScope, String owner) {
        return entityManager
            .createNamedQuery("listDropListEntries")
            .setParameter(OWNER_SCOPE, ownerScope.name())
            .setParameter(OWNER, owner)
            .getResultStream();
    }

    private DropList.Status queryDropList(EntityManager entityManager, OwnerScope ownerScope, String owner, MailAddress sender) {
        String specifiedOwner = ownerScope.equals(OwnerScope.GLOBAL) ? "" : owner;
        return entityManager.createNamedQuery("queryDropListEntry")
            .setParameter(OWNER_SCOPE, ownerScope.name())
            .setParameter(OWNER, specifiedOwner)
            .setParameter(DENIED_ENTITY, List.of(sender.asString(), sender.getDomain().asString()))
            .getResultList().isEmpty() ? DropList.Status.ALLOWED : DropList.Status.BLOCKED;
    }

    private Consumer<PersistenceException> throwStorageExceptionConsumer(String message) {
        return Throwing.<PersistenceException>consumer(e -> {
            throw new StorageException(message, e);
        }).sneakyThrow();
    }
}
