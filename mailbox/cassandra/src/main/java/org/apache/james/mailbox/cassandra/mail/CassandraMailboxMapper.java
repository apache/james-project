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

import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
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
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxMapper implements MailboxMapper {

    private static final String WILDCARD = "%";
    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraMailboxMapper.class);

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathDAOImpl mailboxPathDAO;
    private final CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private final CassandraACLMapper cassandraACLMapper;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;

    @Inject
    public CassandraMailboxMapper(CassandraMailboxDAO mailboxDAO, CassandraMailboxPathDAOImpl mailboxPathDAO, CassandraMailboxPathV2DAO mailboxPathV2DAO, CassandraUserMailboxRightsDAO userMailboxRightsDAO, CassandraACLMapper aclMapper) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathDAO = mailboxPathDAO;
        this.mailboxPathV2DAO = mailboxPathV2DAO;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.cassandraACLMapper = aclMapper;
    }

    @Override
    public void delete(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        Flux.merge(
                mailboxPathDAO.delete(mailbox.generateAssociatedPath()),
                mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()))
            .thenEmpty(mailboxDAO.delete(mailboxId))
            .block();
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        return mailboxPathV2DAO.retrieveId(path)
            .map(CassandraIdAndPath::getCassandraId)
            .flatMap(this::retrieveMailbox)
            .switchIfEmpty(fromPreviousTable(path))
            .blockOptional()
            .orElseThrow(() -> new MailboxNotFoundException(path));
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

    private Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        Mono<MailboxACL> acl = cassandraACLMapper.getACL(mailboxId);
        Mono<Mailbox> simpleMailbox = mailboxDAO.retrieveMailbox(mailboxId);

        return acl.zipWith(simpleMailbox, this::addAcl);
    }

    private Mailbox addAcl(MailboxACL acl, Mailbox mailbox) {
        mailbox.setACL(acl);
        return mailbox;
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        List<Mailbox> mailboxesV2 = toMailboxes(query, mailboxPathV2DAO.listUserMailboxes(query.getFixedNamespace(), query.getFixedUser()));
        List<Mailbox> mailboxesV1 = toMailboxes(query, mailboxPathDAO.listUserMailboxes(query.getFixedNamespace(), query.getFixedUser()));

        List<Mailbox> mailboxesV1NotInV2 = mailboxesV1.stream()
            .filter(mailboxV1 -> mailboxesV2.stream()
                .map(Mailbox::generateAssociatedPath)
                .noneMatch(mailboxV2path -> mailboxV2path.equals(mailboxV1.generateAssociatedPath())))
            .collect(Guavate.toImmutableList());

        return ImmutableList.<Mailbox>builder()
            .addAll(mailboxesV2)
            .addAll(mailboxesV1NotInV2)
            .build();
    }

    private List<Mailbox> toMailboxes(MailboxQuery.UserBound query, Flux<CassandraIdAndPath> listUserMailboxes) {
        return listUserMailboxes
                .filter(idAndPath -> query.isPathMatch(idAndPath.getMailboxPath()))
                .concatMap(this::retrieveMailbox)
                .collectList()
                .block();
    }

    private Mono<Mailbox> retrieveMailbox(CassandraIdAndPath idAndPath) {
        return retrieveMailbox(idAndPath.getCassandraId())
            .switchIfEmpty(ReactorUtils.executeAndEmpty(
                () -> LOGGER.warn("Could not retrieve mailbox {} with path {} in mailbox table.", idAndPath.getCassandraId(), idAndPath.getMailboxPath())));
    }

    @Override
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        CassandraId cassandraId = retrieveId(mailbox);
        mailbox.setMailboxId(cassandraId);
        if (!trySave(mailbox, cassandraId)) {
            throw new MailboxExistsException(mailbox.generateAssociatedPath().asString());
        }
        return cassandraId;
    }

    private boolean trySave(Mailbox cassandraMailbox, CassandraId cassandraId) {
        return mailboxPathV2DAO.save(cassandraMailbox.generateAssociatedPath(), cassandraId)
            .filter(isCreated -> isCreated)
            .flatMap(mailboxHasCreated -> mailboxDAO.retrieveMailbox(cassandraId)
                .flatMap(mailbox -> mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()))
                .then(mailboxDAO.save(cassandraMailbox))
                .thenReturn(true))
            .switchIfEmpty(Mono.just(false))
            .block();
    }

    private CassandraId retrieveId(Mailbox cassandraMailbox) {
        if (cassandraMailbox.getMailboxId() == null) {
            return CassandraId.timeBased();
        } else {
            return (CassandraId) cassandraMailbox.getMailboxId();
        }
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) {
        return Flux.merge(
                mailboxPathDAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()),
                mailboxPathV2DAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()))
            .filter(idAndPath -> idAndPath.getMailboxPath().getName().startsWith(mailbox.getName() + String.valueOf(delimiter)))
            .hasElements()
            .block();
    }

    @Override
    public List<Mailbox> list() {
        return mailboxDAO.retrieveAllMailboxes()
            .flatMap(this::toMailboxWithAcl)
            .map(simpleMailboxes -> (Mailbox) simpleMailboxes)
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

    private String constructEscapedRegexForMailboxNameMatching(MailboxPath path) {
        return Collections
            .list(new StringTokenizer(path.getName(), WILDCARD, true))
            .stream()
            .map(this::tokenToPatternPart)
            .collect(Collectors.joining());
    }

    private String tokenToPatternPart(Object token) {
        if (token.equals(WILDCARD)) {
            return ".*";
        } else {
            return Pattern.quote((String) token);
        }
    }

    private Mono<Mailbox> toMailboxWithAcl(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.getACL(cassandraId)
            .map(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            });
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) {
        return userMailboxRightsDAO.listRightsForUser(userName)
            .filter(mailboxId -> authorizedMailbox(mailboxId.getRight(), right))
            .map(Pair::getLeft)
            .flatMap(this::retrieveMailbox)
            .map(simpleMailboxes -> (Mailbox) simpleMailboxes)
            .collectList()
            .block();
    }

    private boolean authorizedMailbox(MailboxACL.Rfc4314Rights rights, Right right) {
        return rights.contains(right);
    }

}
