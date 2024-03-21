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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.WEAK;

import java.security.SecureRandom;
import java.time.Duration;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.util.FunctionalUtils;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class CassandraMailboxMapper implements MailboxMapper {
    private static final int MAX_RETRY = 5;
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMillis(1000);
    private static final int CONCURRENCY = 10;

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathV3DAO mailboxPathV3DAO;
    private final ACLMapper aclMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraConfiguration cassandraConfiguration;
    private final SecureRandom secureRandom;

    @Inject
    public CassandraMailboxMapper(CassandraMailboxDAO mailboxDAO,
                                  CassandraMailboxPathV3DAO mailboxPathV3DAO,
                                  CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                  ACLMapper aclMapper,
                                  CassandraConfiguration cassandraConfiguration) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathV3DAO = mailboxPathV3DAO;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.aclMapper = aclMapper;
        this.cassandraConfiguration = cassandraConfiguration;
        this.secureRandom = new SecureRandom();
    }

    private Mono<Mailbox> performReadRepair(CassandraId id) {
        if (shouldReadRepair()) {
            return mailboxDAO.retrieveMailbox(id)
                .flatMap(mailboxEntry -> SolveMailboxInconsistenciesService.Inconsistency
                    .detectMailboxDaoInconsistency(mailboxEntry,
                        mailboxPathV3DAO.retrieve(mailboxEntry.generateAssociatedPath(), STRONG))
                    .flatMap(inconsistency ->
                        inconsistency.fix(new SolveMailboxInconsistenciesService.Context(), mailboxDAO, mailboxPathV3DAO)
                            .then(Mono.just(mailboxEntry))));
        }
        return mailboxDAO.retrieveMailbox(id);
    }

    private Mono<Mailbox> performReadRepair(MailboxPath path) {
        if (shouldReadRepair()) {
            return mailboxPathV3DAO.retrieve(path, STRONG)
                .flatMap(this::performPathReadRepair);
        }
        return mailboxPathV3DAO.retrieve(path, consistencyChoice());
    }

    private JamesExecutionProfiles.ConsistencyChoice consistencyChoice() {
        if (cassandraConfiguration.isMailboxReadStrongConsistency()) {
            return STRONG;
        } else {
            return WEAK;
        }
    }

    private Flux<Mailbox> performReadRepair(Flux<Mailbox> pathEntries) {
        return pathEntries.flatMap(mailboxPathEntry -> {
            if (shouldReadRepair()) {
                return performPathReadRepair(mailboxPathEntry);
            }
            return Mono.just(mailboxPathEntry);
        }, CONCURRENCY);
    }

    private Mono<Mailbox> performPathReadRepair(Mailbox mailboxPathEntry) {
        return SolveMailboxInconsistenciesService.Inconsistency
            .detectMailboxPathDaoInconsistency(mailboxPathEntry,
                mailboxDAO.retrieveMailbox((CassandraId) mailboxPathEntry.getMailboxId()))
            .flatMap(inconsistency ->
                inconsistency.fix(new SolveMailboxInconsistenciesService.Context(), mailboxDAO, mailboxPathV3DAO)
                    .then(Mono.just(mailboxPathEntry)));
    }

    private boolean shouldReadRepair() {
        return cassandraConfiguration.getMailboxReadRepair() > 0
            && secureRandom.nextFloat() < cassandraConfiguration.getMailboxReadRepair();
    }


    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return deletePath(mailbox)
            .thenEmpty(mailboxDAO.delete(mailboxId)
                .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)));
    }

    private Mono<Void> deletePath(Mailbox mailbox) {
        return mailboxPathV3DAO.delete(mailbox.generateAssociatedPath());
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath path) {
        return performReadRepair(path)
            .flatMap(this::addAcl);
    }

    private Mono<Mailbox> addAcl(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return aclMapper.getACL(mailboxId)
            .map(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            })
            .switchIfEmpty(Mono.just(mailbox));
    }

    @Override
    public Mono<Boolean> pathExists(MailboxPath path) {
        return performReadRepair(path)
            .hasElement();
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId id) {
        CassandraId mailboxId = (CassandraId) id;
        return retrieveMailbox(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(id)));
    }

    private Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        Mono<MailboxACL> acl = retrieveAcl(mailboxId);
        Mono<Mailbox> simpleMailbox = performReadRepair(mailboxId);

        return acl.zipWith(simpleMailbox, this::addAcl);
    }

    private Mono<MailboxACL> retrieveAcl(CassandraId mailboxId) {
        return aclMapper.getACL(mailboxId)
            .defaultIfEmpty(MailboxACL.EMPTY);
    }

    private Mailbox addAcl(MailboxACL acl, Mailbox mailbox) {
        mailbox.setACL(acl);
        return mailbox;
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        String fixedNamespace = query.getFixedNamespace();
        Username fixedUser = query.getFixedUser();

        return performReadRepair(listMailboxes(fixedNamespace, fixedUser))
            .filter(mailbox -> query.isPathMatch(mailbox.generateAssociatedPath()))
            .flatMap(this::addAcl, CONCURRENCY);
    }

    private Flux<Mailbox> listMailboxes(String fixedNamespace, Username fixedUser) {
        return mailboxPathV3DAO.listUserMailboxes(fixedNamespace, fixedUser, consistencyChoice());
    }

    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        CassandraId cassandraId = CassandraId.timeBased();
        Mailbox mailbox = new Mailbox(mailboxPath, uidValidity, cassandraId);

        return mailboxPathV3DAO.save(mailbox)
            .filter(isCreated -> isCreated)
            .flatMap(mailboxHasCreated -> persistMailboxEntity(mailbox)
                .thenReturn(mailbox))
            .switchIfEmpty(Mono.error(() -> new MailboxExistsException(mailbox.generateAssociatedPath().asString())));
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return tryRename(mailbox, cassandraId)
            .filter(FunctionalUtils.identityPredicate())
            .switchIfEmpty(Mono.error(() -> new MailboxExistsException(mailbox.generateAssociatedPath().asString())))
            .thenReturn(cassandraId);
    }

    private Mono<Boolean> tryRename(Mailbox cassandraMailbox, CassandraId cassandraId) {
        return mailboxDAO.retrieveMailbox(cassandraId)
            .flatMap(mailbox -> mailboxPathV3DAO.save(cassandraMailbox)
                .filter(isCreated -> isCreated)
                .flatMap(mailboxHasCreated -> deletePreviousMailboxPathReference(mailbox.generateAssociatedPath())
                    .then(persistMailboxEntity(cassandraMailbox))
                    .thenReturn(true))
                .defaultIfEmpty(false))
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(cassandraId)));
    }

    private Mono<Void> persistMailboxEntity(Mailbox cassandraMailbox) {
        return mailboxDAO.save(cassandraMailbox)
            .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF));
    }

    private Mono<Void> deletePreviousMailboxPathReference(MailboxPath mailboxPath) {
        return mailboxPathV3DAO.delete(mailboxPath)
            .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF));
    }

    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        return performReadRepair(listMailboxes(mailbox.getNamespace(), mailbox.getUser()))
            .filter(idAndPath -> isPathChildOfMailbox(idAndPath, mailbox, delimiter))
            .hasElements();
    }

    private boolean isPathChildOfMailbox(Mailbox candidate, Mailbox mailbox, char delimiter) {
        return candidate.generateAssociatedPath().getName().startsWith(mailbox.getName() + delimiter);
    }

    @Override
    public Flux<Mailbox> list() {
        return performReadRepair(mailboxDAO.retrieveAllMailboxes())
            .flatMap(this::addAcl, CONCURRENCY);
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return aclMapper.updateACL(cassandraId, mailboxACLCommand);
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return aclMapper.setACL(cassandraId, mailboxACL);
    }

    @Override
    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, Right right) {
        return performReadRepair(
            userMailboxRightsDAO.listRightsForUser(userName)
                .filter(mailboxId -> mailboxId.getRight().contains(right))
                .map(Pair::getLeft)
                .flatMap(this::retrieveMailbox, CONCURRENCY));
    }
}
