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
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.DriverExceptionHelper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CassandraMailboxMapper implements MailboxMapper {

    public static final String WILDCARD = "%";
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
        FluentFutureStream.ofFutures(mailboxPathDAO.delete(mailbox.generateAssociatedPath()), mailboxPathV2DAO.delete(mailbox.generateAssociatedPath()))
            .thenComposeOnAll(any -> mailboxDAO.delete(mailboxId))
            .join();
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        try {
            return mailboxPathV2DAO.retrieveId(path)
                .thenCompose(cassandraIdOptional ->
                    cassandraIdOptional
                        .map(CassandraIdAndPath::getCassandraId)
                        .map(this::retrieveMailbox)
                        .orElseGet(Throwing.supplier(() -> fromPreviousTable(path))))
                .join()
                .orElseThrow(() -> new MailboxNotFoundException(path));
        } catch (CompletionException e) {
            throw DriverExceptionHelper.handleStorageException(e);
        }
    }

    private CompletableFuture<Optional<SimpleMailbox>> fromPreviousTable(MailboxPath path) throws MailboxException {
        try {
            return mailboxPathDAO.retrieveId(path)
                .thenCompose(cassandraIdOptional ->
                    cassandraIdOptional
                        .map(CassandraIdAndPath::getCassandraId)
                        .map(this::retrieveMailbox)
                        .orElse(CompletableFuture.completedFuture(Optional.empty())))
                .thenCompose(maybeMailbox -> maybeMailbox.map(this::migrate)
                    .orElse(CompletableFuture.completedFuture(maybeMailbox)));
        } catch (CompletionException e) {
            throw DriverExceptionHelper.handleStorageException(e);
        }
    }

    private CompletableFuture<Optional<SimpleMailbox>> migrate(SimpleMailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return mailboxPathV2DAO.save(mailbox.generateAssociatedPath(), mailboxId)
            .thenCompose(success -> deleteIfSuccess(mailbox, success))
            .thenApply(any -> Optional.of(mailbox));
    }

    private CompletionStage<Void> deleteIfSuccess(SimpleMailbox mailbox, boolean success) {
        if (success) {
            return mailboxPathDAO.delete(mailbox.generateAssociatedPath());
        }
        LOGGER.info("Concurrent execution lead to data race while migrating {} to 'mailboxPathV2DAO'.",
            mailbox.generateAssociatedPath());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException {
        CassandraId mailboxId = (CassandraId) id;
        return retrieveMailbox(mailboxId)
            .join()
            .orElseThrow(() -> new MailboxNotFoundException(id));
    }

    private CompletableFuture<Optional<SimpleMailbox>> retrieveMailbox(CassandraId mailboxId) {
        CompletableFuture<MailboxACL> aclCompletableFuture = cassandraACLMapper.getACL(mailboxId);

        CompletableFuture<Optional<SimpleMailbox>> simpleMailboxFuture = mailboxDAO.retrieveMailbox(mailboxId);

        return CompletableFutureUtil.combine(
            aclCompletableFuture,
            simpleMailboxFuture,
            this::addAcl);
    }

    private Optional<SimpleMailbox> addAcl(MailboxACL acl, Optional<SimpleMailbox> mailboxOptional) {
        mailboxOptional.ifPresent(mailbox -> mailbox.setACL(acl));
        return mailboxOptional;
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) {
        List<Mailbox> mailboxesV2 = toMailboxes(path, mailboxPathV2DAO.listUserMailboxes(path.getNamespace(), path.getUser()));
        List<Mailbox> mailboxesV1 = toMailboxes(path, mailboxPathDAO.listUserMailboxes(path.getNamespace(), path.getUser()));

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

    private List<Mailbox> toMailboxes(MailboxPath path, CompletableFuture<Stream<CassandraIdAndPath>> listUserMailboxes) {
        Pattern regex = Pattern.compile(constructEscapedRegexForMailboxNameMatching(path));
        
        return FluentFutureStream.of(listUserMailboxes)
                .filter(idAndPath -> regex.matcher(idAndPath.getMailboxPath().getName()).matches())
                .thenFlatComposeOnOptional(this::retrieveMailbox)
                .join()
                .collect(Guavate.toImmutableList());
    }

    private CompletableFuture<Optional<SimpleMailbox>> retrieveMailbox(CassandraIdAndPath idAndPath) {
        return retrieveMailbox(idAndPath.getCassandraId())
            .thenApply(optional -> OptionalUtils.executeIfEmpty(optional,
                () -> LOGGER.warn("Could not retrieve mailbox {} with path {} in mailbox table.", idAndPath.getCassandraId(), idAndPath.getMailboxPath())));
    }

    @Override
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox instanceof SimpleMailbox);
        SimpleMailbox cassandraMailbox = (SimpleMailbox) mailbox;

        CassandraId cassandraId = retrieveId(cassandraMailbox);
        cassandraMailbox.setMailboxId(cassandraId);
        try {
            boolean applied = trySave(cassandraMailbox, cassandraId).join();
            if (!applied) {
                throw new MailboxExistsException(mailbox.generateAssociatedPath().asString());
            }
        } catch (CompletionException e) {
            throw DriverExceptionHelper.handleStorageException(e);
        }
        return cassandraId;
    }

    private CompletableFuture<Boolean> trySave(SimpleMailbox cassandraMailbox, CassandraId cassandraId) {
        return mailboxPathV2DAO.save(cassandraMailbox.generateAssociatedPath(), cassandraId)
            .thenCompose(CompletableFutureUtil.composeIfTrue(
                () -> retrieveMailbox(cassandraId)
                    .thenCompose(optional -> CompletableFuture
                        .allOf(optional
                                .map(storedMailbox -> mailboxPathV2DAO.delete(storedMailbox.generateAssociatedPath()))
                                .orElse(CompletableFuture.completedFuture(null)),
                            mailboxDAO.save(cassandraMailbox)))));
    }

    private CassandraId retrieveId(SimpleMailbox cassandraMailbox) {
        if (cassandraMailbox.getMailboxId() == null) {
            return CassandraId.timeBased();
        } else {
            return (CassandraId) cassandraMailbox.getMailboxId();
        }
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) {
        return ImmutableList.of(
                mailboxPathDAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()),
                mailboxPathV2DAO.listUserMailboxes(mailbox.getNamespace(), mailbox.getUser()))
            .stream()
            .map(CompletableFuture::join)
            .flatMap(Function.identity())
            .anyMatch(idAndPath -> idAndPath.getMailboxPath().getName().startsWith(mailbox.getName() + String.valueOf(delimiter)));
    }

    @Override
    public List<Mailbox> list() {
        return mailboxDAO.retrieveAllMailboxes()
            .thenComposeOnAll(this::toMailboxWithAclFuture)
            .join()
            .collect(Guavate.toImmutableList());
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

    private CompletableFuture<SimpleMailbox> toMailboxWithAclFuture(SimpleMailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.getACL(cassandraId)
            .thenApply(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            });
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(String userName, Right right) {
        return FluentFutureStream.of(userMailboxRightsDAO.listRightsForUser(userName)
            .thenApply(map -> toAuthorizedMailboxIds(map, right)))
            .thenFlatComposeOnOptional(this::retrieveMailbox)
            .join()
            .collect(Guavate.toImmutableList());
    }

    private Stream<CassandraId> toAuthorizedMailboxIds(Map<CassandraId, MailboxACL.Rfc4314Rights> map, Right right) {
        return map.entrySet()
            .stream()
            .filter(Throwing.predicate(entry -> entry.getValue().contains(right)))
            .map(Map.Entry::getKey);
    }

}
