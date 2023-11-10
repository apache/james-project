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

import java.util.NoSuchElementException;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.postgres.JPAId;
import org.apache.james.mailbox.postgres.JPATransactionalMapper;
import org.apache.james.mailbox.postgres.mail.model.JPAMailbox;
import org.apache.james.mailbox.store.MailboxExpressionBackwardCompatibility;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Data access management for mailbox.
 */
public class JPAMailboxMapper extends JPATransactionalMapper implements MailboxMapper {

    private static final char SQL_WILDCARD_CHAR = '%';
    private String lastMailboxName;
    
    public JPAMailboxMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    /**
     * Commit the transaction. If the commit fails due a conflict in a unique key constraint a {@link MailboxExistsException}
     * will get thrown
     */
    @Override
    protected void commit() throws MailboxException {
        try {
            getEntityManager().getTransaction().commit();
        } catch (PersistenceException e) {
            if (e instanceof EntityExistsException) {
                throw new MailboxExistsException(lastMailboxName);
            }
            if (e instanceof RollbackException) {
                Throwable t = e.getCause();
                if (t instanceof EntityExistsException) {
                    throw new MailboxExistsException(lastMailboxName);
                }
            }
            throw new MailboxException("Commit of transaction failed", e);
        }
    }
    
    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        return assertPathIsNotAlreadyUsedByAnotherMailbox(mailboxPath)
            .then(Mono.fromCallable(() -> {
                this.lastMailboxName = mailboxPath.getName();
                JPAMailbox persistedMailbox = new JPAMailbox(mailboxPath, uidValidity);
                getEntityManager().persist(persistedMailbox);

                return new Mailbox(mailboxPath, uidValidity, persistedMailbox.getMailboxId());
            }).subscribeOn(Schedulers.boundedElastic()))
            .onErrorMap(PersistenceException.class, e -> new MailboxException("Save of mailbox " + mailboxPath.getName() + " failed", e));
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        return assertPathIsNotAlreadyUsedByAnotherMailbox(mailbox.generateAssociatedPath())
            .then(Mono.fromCallable(() -> {
                this.lastMailboxName = mailbox.getName();
                JPAMailbox persistedMailbox = jpaMailbox(mailbox);

                getEntityManager().persist(persistedMailbox);
                return (MailboxId) persistedMailbox.getMailboxId();
            }).subscribeOn(Schedulers.boundedElastic()))
            .onErrorMap(PersistenceException.class, e -> new MailboxException("Save of mailbox " + mailbox.getName() + " failed", e));
    }

    private JPAMailbox jpaMailbox(Mailbox mailbox) throws MailboxException {
        JPAMailbox result = loadJpaMailbox(mailbox.getMailboxId());
        result.setNamespace(mailbox.getNamespace());
        result.setUser(mailbox.getUser().asString());
        result.setName(mailbox.getName());
        return result;
    }

    private Mono<Void> assertPathIsNotAlreadyUsedByAnotherMailbox(MailboxPath mailboxPath) {
        return findMailboxByPath(mailboxPath)
            .flatMap(ignored -> Mono.error(new MailboxExistsException(mailboxPath.getName())));
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath mailboxPath)  {
        return Mono.fromCallable(() -> getEntityManager().createNamedQuery("findMailboxByNameWithUser", JPAMailbox.class)
            .setParameter("nameParam", mailboxPath.getName())
            .setParameter("namespaceParam", mailboxPath.getNamespace())
            .setParameter("userParam", mailboxPath.getUser().asString())
            .getSingleResult()
            .toMailbox())
            .onErrorResume(NoResultException.class, e -> Mono.empty())
            .onErrorResume(NoSuchElementException.class, e -> Mono.empty())
            .onErrorResume(PersistenceException.class, e -> Mono.error(new MailboxException("Exception upon JPA execution", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId id) {
        return Mono.fromCallable(() -> loadJpaMailbox(id).toMailbox())
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(PersistenceException.class, e -> new MailboxException("Search of mailbox " + id.serialize() + " failed", e));
    }

    private JPAMailbox loadJpaMailbox(MailboxId id) throws MailboxNotFoundException {
        JPAId mailboxId = (JPAId)id;
        try {
            return getEntityManager().createNamedQuery("findMailboxById", JPAMailbox.class)
                .setParameter("idParam", mailboxId.getRawId())
                .getSingleResult();
        } catch (NoResultException e) {
            throw new MailboxNotFoundException(mailboxId);
        }
    }

    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        return Mono.fromRunnable(() -> {
            JPAId mailboxId = (JPAId) mailbox.getMailboxId();
            getEntityManager().createNamedQuery("deleteMessages").setParameter("idParam", mailboxId.getRawId()).executeUpdate();
            JPAMailbox jpaMailbox = getEntityManager().find(JPAMailbox.class, mailboxId.getRawId());
            getEntityManager().remove(jpaMailbox);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(PersistenceException.class, e -> new MailboxException("Delete of mailbox " + mailbox + " failed", e))
        .then();
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        String pathLike = MailboxExpressionBackwardCompatibility.getPathLike(query);
        return Mono.fromCallable(() -> findMailboxWithPathLikeTypedQuery(query.getFixedNamespace(), query.getFixedUser(), pathLike))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapIterable(TypedQuery::getResultList)
            .map(JPAMailbox::toMailbox)
            .filter(query::matches)
            .onErrorMap(PersistenceException.class, e -> new MailboxException("Search of mailbox " + query + " failed", e));
    }

    private TypedQuery<JPAMailbox> findMailboxWithPathLikeTypedQuery(String namespace, Username username, String pathLike) {
        return getEntityManager().createNamedQuery("findMailboxWithNameLikeWithUser", JPAMailbox.class)
            .setParameter("nameParam", pathLike)
            .setParameter("namespaceParam", namespace)
            .setParameter("userParam", username.asString());
    }
    
    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        final String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR; 

        return Mono.defer(() -> Mono.justOrEmpty((Long) getEntityManager()
                .createNamedQuery("countMailboxesWithNameLikeWithUser")
                .setParameter("nameParam", name)
                .setParameter("namespaceParam", mailbox.getNamespace())
                .setParameter("userParam", mailbox.getUser().asString())
                .getSingleResult()))
            .subscribeOn(Schedulers.boundedElastic())
            .filter(numberOfChildMailboxes -> numberOfChildMailboxes > 0)
            .hasElement();
    }

    @Override
    public Flux<Mailbox> list() {
        return Mono.fromCallable(() -> getEntityManager().createNamedQuery("listMailboxes", JPAMailbox.class))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapIterable(TypedQuery::getResultList)
            .onErrorMap(PersistenceException.class, e -> new MailboxException("Delete of mailboxes failed", e))
            .map(JPAMailbox::toMailbox);
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        return Mono.fromCallable(() -> {
            MailboxACL oldACL = mailbox.getACL();
            MailboxACL newACL = mailbox.getACL().apply(mailboxACLCommand);
            mailbox.setACL(newACL);
            return ACLDiff.computeDiff(oldACL, newACL);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        return Mono.fromCallable(() -> {
            MailboxACL oldMailboxAcl = mailbox.getACL();
            mailbox.setACL(mailboxACL);
            return ACLDiff.computeDiff(oldMailboxAcl, mailboxACL);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, Right right) {
        return Flux.empty();
    }
}
