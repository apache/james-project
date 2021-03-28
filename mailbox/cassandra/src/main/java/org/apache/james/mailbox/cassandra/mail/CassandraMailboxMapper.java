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

import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.WEAK;

import java.security.SecureRandom;
import java.time.Duration;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
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
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class CassandraMailboxMapper implements MailboxMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMailboxMapper.class);

    private static final int MAX_RETRY = 5;
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMillis(1000);
    private static final SchemaVersion MAILBOX_PATH_V_3_MIGRATION_PERFORMED_VERSION = new SchemaVersion(8);
    private static final int CONCURRENCY = 10;

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathDAOImpl mailboxPathDAO;
    private final CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private final CassandraMailboxPathV3DAO mailboxPathV3DAO;
    private final CassandraACLMapper cassandraACLMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraSchemaVersionManager versionManager;
    private final CassandraConfiguration cassandraConfiguration;
    private final SecureRandom secureRandom;

    @Inject
    public CassandraMailboxMapper(CassandraMailboxDAO mailboxDAO,
                                  CassandraMailboxPathDAOImpl mailboxPathDAO,
                                  CassandraMailboxPathV2DAO mailboxPathV2DAO,
                                  CassandraMailboxPathV3DAO mailboxPathV3DAO,
                                  CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                  CassandraACLMapper aclMapper,
                                  CassandraSchemaVersionManager versionManager,
                                  CassandraConfiguration cassandraConfiguration) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.mailboxPathV2DAO = mailboxPathV2DAO;
        this.mailboxPathV3DAO = mailboxPathV3DAO;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.cassandraACLMapper = aclMapper;
        this.versionManager = versionManager;
        this.cassandraConfiguration = cassandraConfiguration;
        this.secureRandom = new SecureRandom();
    }

    private Mono<Boolean> needMailboxPathPreviousVersionsSupport() {
        return versionManager.isBefore(MAILBOX_PATH_V_3_MIGRATION_PERFORMED_VERSION);
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

    private CassandraConsistenciesConfiguration.ConsistencyChoice consistencyChoice() {
        if (cassandraConfiguration.isMailboxReadStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
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
        return secureRandom.nextFloat() < cassandraConfiguration.getMailboxReadRepair();
    }


    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return deletePath(mailbox)
            .thenEmpty(mailboxDAO.delete(mailboxId)
                .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)));
    }

    private Flux<Void> deletePath(Mailbox mailbox) {
        return needMailboxPathPreviousVersionsSupport()
            .flatMapMany(needSupport -> {
                if (needSupport) {
                    return Flux.merge(
                        mailboxPathDAO.delete(mailbox.generateAssociatedPath()),
                        mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()),
                        mailboxPathV3DAO.delete(mailbox.generateAssociatedPath()));
                }
                return Flux.from(mailboxPathV3DAO.delete(mailbox.generateAssociatedPath()));
            });
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath path) {
        return performReadRepair(path)
            .switchIfEmpty(fromPreviousTable(path))
            .flatMap(this::addAcl);
    }

    private Mono<Mailbox> addAcl(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.getACL(mailboxId)
            .map(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            })
            .switchIfEmpty(Mono.just(mailbox));
    }

    @Override
    public Mono<Boolean> pathExists(MailboxPath path) {
        return performReadRepair(path)
            .switchIfEmpty(fromPreviousTable(path))
            .hasElement();
    }

    private Mono<Mailbox> fromPreviousTable(MailboxPath path) {
        return mailboxPathV2DAO.retrieveId(path)
            .switchIfEmpty(mailboxPathDAO.retrieveId(path))
            .map(CassandraIdAndPath::getCassandraId)
            .flatMap(mailboxDAO::retrieveMailbox)
            .flatMap(this::migrate);
    }

    private Mono<Mailbox> migrate(Mailbox mailbox) {
        return mailboxPathV3DAO.save(mailbox)
            .flatMap(success -> deleteIfSuccess(mailbox, success))
            .thenReturn(mailbox);
    }

    private Mono<Void> deleteIfSuccess(Mailbox mailbox, boolean success) {
        if (success) {
            return mailboxPathDAO.delete(mailbox.generateAssociatedPath())
                .then(mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()));
        }
        LOGGER.info("Concurrent execution lead to data race while migrating {} to 'mailboxPathV3DAO'.",
            mailbox.generateAssociatedPath());
        return Mono.empty();
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
        return cassandraACLMapper.getACL(mailboxId)
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
            .distinct(Mailbox::generateAssociatedPath)
            .flatMap(this::addAcl, CONCURRENCY);
    }

    private Flux<Mailbox> listMailboxes(String fixedNamespace, Username fixedUser) {
        return needMailboxPathPreviousVersionsSupport()
            .flatMapMany(needSupport -> {
                if (needSupport) {
                    return Flux.concat(
                        mailboxPathV3DAO.listUserMailboxes(fixedNamespace, fixedUser, consistencyChoice()),
                        Flux.concat(
                                mailboxPathV2DAO.listUserMailboxes(fixedNamespace, fixedUser),
                                mailboxPathDAO.listUserMailboxes(fixedNamespace, fixedUser))
                            .flatMap(this::retrieveMailbox, CONCURRENCY));
                }
                return mailboxPathV3DAO.listUserMailboxes(fixedNamespace, fixedUser, consistencyChoice());
            });
    }

    private Mono<Mailbox> retrieveMailbox(CassandraIdAndPath idAndPath) {
        return retrieveMailbox(idAndPath.getCassandraId())
            .switchIfEmpty(ReactorUtils.executeAndEmpty(
                () -> LOGGER.warn("Could not retrieve mailbox {} with path {} in mailbox table.", idAndPath.getCassandraId(), idAndPath.getMailboxPath())));
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
            .flatMap(this::toMailboxWithAcl, CONCURRENCY);
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.updateACL(cassandraId, mailboxACLCommand);
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.setACL(cassandraId, mailboxACL);
    }

    private Mono<Mailbox> toMailboxWithAcl(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return retrieveAcl(cassandraId)
            .map(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            });
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
