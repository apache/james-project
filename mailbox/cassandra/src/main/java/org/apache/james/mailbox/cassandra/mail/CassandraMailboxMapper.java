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

import java.time.Duration;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
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
import org.apache.james.mailbox.store.mail.MailboxMapper;
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
    private static final SchemaVersion MAILBOX_PATH_V_2_MIGRATION_PERFORMED_VERSION = new SchemaVersion(6);

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathDAOImpl mailboxPathDAO;
    private final CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private final CassandraACLMapper cassandraACLMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraSchemaVersionManager versionManager;

    @Inject
    public CassandraMailboxMapper(CassandraMailboxDAO mailboxDAO,
                                  CassandraMailboxPathDAOImpl mailboxPathDAO,
                                  CassandraMailboxPathV2DAO mailboxPathV2DAO,
                                  CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                  CassandraACLMapper aclMapper,
                                  CassandraSchemaVersionManager versionManager) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.mailboxPathV2DAO = mailboxPathV2DAO;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.cassandraACLMapper = aclMapper;
        this.versionManager = versionManager;
    }

    private Mono<Boolean> needMailboxPathV1Support() {
        return versionManager.isBefore(MAILBOX_PATH_V_2_MIGRATION_PERFORMED_VERSION);
    }

    @Override
    public void delete(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        deletePath(mailbox)
            .thenEmpty(mailboxDAO.delete(mailboxId)
                .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)))
            .block();
    }

    private Flux<Void> deletePath(Mailbox mailbox) {
        return needMailboxPathV1Support()
            .flatMapMany(needSupport -> {
                if (needSupport) {
                    return Flux.merge(
                        mailboxPathDAO.delete(mailbox.generateAssociatedPath()),
                        mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()));
                }
                return Flux.from(mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()));
            });
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath path) {
        return mailboxPathV2DAO.retrieveId(path)
            .map(CassandraIdAndPath::getCassandraId)
            .flatMap(this::retrieveMailbox)
            .switchIfEmpty(fromPreviousTable(path));
    }

    @Override
    public Mono<Boolean> pathExists(MailboxPath mailboxName) {
        return mailboxPathV2DAO.retrieveId(mailboxName)
            .switchIfEmpty(mailboxPathDAO.retrieveId(mailboxName))
            .hasElement();
    }

    private Mono<Mailbox> fromPreviousTable(MailboxPath path) {
        return mailboxPathDAO.retrieveId(path)
            .map(CassandraIdAndPath::getCassandraId)
            .flatMap(this::retrieveMailbox)
            .flatMap(this::migrate);
    }

    private Mono<Mailbox> migrate(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return mailboxPathV2DAO.save(mailbox.generateAssociatedPath(), mailboxId)
            .flatMap(success -> deleteIfSuccess(mailbox, success))
            .thenReturn(mailbox);
    }

    private Mono<Void> deleteIfSuccess(Mailbox mailbox, boolean success) {
        if (success) {
            return mailboxPathDAO.delete(mailbox.generateAssociatedPath());
        }
        LOGGER.info("Concurrent execution lead to data race while migrating {} to 'mailboxPathV2DAO'.",
            mailbox.generateAssociatedPath());
        return Mono.empty();
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException {
        CassandraId mailboxId = (CassandraId) id;
        return retrieveMailbox(mailboxId)
            .blockOptional()
            .orElseThrow(() -> new MailboxNotFoundException(id));
    }

    @Override
    public Mono<Mailbox> findMailboxByIdReactive(MailboxId id) {
        CassandraId mailboxId = (CassandraId) id;
        return retrieveMailbox(mailboxId);
    }

    private Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        Mono<MailboxACL> acl = retrieveAcl(mailboxId);
        Mono<Mailbox> simpleMailbox = mailboxDAO.retrieveMailbox(mailboxId);

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

        return listPaths(fixedNamespace, fixedUser)
            .filter(idAndPath -> query.isPathMatch(idAndPath.getMailboxPath()))
            .distinct(CassandraIdAndPath::getMailboxPath)
            .concatMap(this::retrieveMailbox);
    }

    private Flux<CassandraIdAndPath> listPaths(String fixedNamespace, Username fixedUser) {
        return needMailboxPathV1Support()
            .flatMapMany(needSupport -> {
                if (needSupport) {
                    return Flux.concat(mailboxPathV2DAO.listUserMailboxes(fixedNamespace, fixedUser),
                        mailboxPathDAO.listUserMailboxes(fixedNamespace, fixedUser));
                }
                return mailboxPathV2DAO.listUserMailboxes(fixedNamespace, fixedUser);
            });
    }

    private Mono<Mailbox> retrieveMailbox(CassandraIdAndPath idAndPath) {
        return retrieveMailbox(idAndPath.getCassandraId())
            .switchIfEmpty(ReactorUtils.executeAndEmpty(
                () -> LOGGER.warn("Could not retrieve mailbox {} with path {} in mailbox table.", idAndPath.getCassandraId(), idAndPath.getMailboxPath())));
    }

    @Override
    public Mailbox create(MailboxPath mailboxPath, UidValidity uidValidity) throws MailboxException {
        CassandraId cassandraId = CassandraId.timeBased();
        Mailbox mailbox = new Mailbox(mailboxPath, uidValidity, cassandraId);

        if (!tryCreate(mailbox, cassandraId).block()) {
            throw new MailboxExistsException(mailbox.generateAssociatedPath().asString());
        }
        return mailbox;
    }

    private Mono<Boolean> tryCreate(Mailbox cassandraMailbox, CassandraId cassandraId) {
        return mailboxPathV2DAO.save(cassandraMailbox.generateAssociatedPath(), cassandraId)
            .filter(isCreated -> isCreated)
            .flatMap(mailboxHasCreated -> persistMailboxEntity(cassandraMailbox)
                .thenReturn(true))
            .defaultIfEmpty(false);
    }

    @Override
    public MailboxId rename(Mailbox mailbox) throws MailboxException {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        try {
            if (!tryRename(mailbox, cassandraId).block()) {
                throw new MailboxExistsException(mailbox.generateAssociatedPath().asString());
            }
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxNotFoundException) {
                throw (MailboxNotFoundException)e.getCause();
            }
            throw e;
        }
        return cassandraId;
    }

    private Mono<Boolean> tryRename(Mailbox cassandraMailbox, CassandraId cassandraId) {
        return mailboxDAO.retrieveMailbox(cassandraId)
            .flatMap(mailbox -> mailboxPathV2DAO.save(cassandraMailbox.generateAssociatedPath(), cassandraId)
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
        return mailboxPathV2DAO.delete(mailboxPath)
            .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF));
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) {
        return Flux.merge(
                mailboxPathDAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()),
                mailboxPathV2DAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()))
            .filter(idAndPath -> isPathChildOfMailbox(idAndPath, mailbox, delimiter))
            .hasElements()
            .block();
    }

    private boolean isPathChildOfMailbox(CassandraIdAndPath idAndPath, Mailbox mailbox, char delimiter) {
        return idAndPath.getMailboxPath().getName().startsWith(mailbox.getName() + String.valueOf(delimiter));
    }

    @Override
    public List<Mailbox> list() {
        return mailboxDAO.retrieveAllMailboxes()
            .flatMap(this::toMailboxWithAcl)
            .collectList()
            .block();
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.updateACL(cassandraId, mailboxACLCommand);
    }

    @Override
    public ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.setACL(cassandraId, mailboxACL);
    }

    @Override
    public void endRequest() {
        // Do nothing
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
        return userMailboxRightsDAO.listRightsForUser(userName)
            .filter(mailboxId -> mailboxId.getRight().contains(right))
            .map(Pair::getLeft)
            .flatMap(this::retrieveMailbox);
    }
}
