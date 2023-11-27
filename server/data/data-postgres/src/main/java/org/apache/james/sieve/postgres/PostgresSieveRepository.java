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

package org.apache.james.sieve.postgres;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.sieve.postgres.model.JPASieveScript;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresSieveRepository implements SieveRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSieveRepository.class);

    private final TransactionRunner transactionRunner;
    private final PostgresSieveQuotaDAO postgresSieveQuotaDAO;

    @Inject
    public PostgresSieveRepository(EntityManagerFactory entityManagerFactory, PostgresSieveQuotaDAO postgresSieveQuotaDAO) {
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
        this.postgresSieveQuotaDAO = postgresSieveQuotaDAO;
    }

    @Override
    public void haveSpace(Username username, ScriptName name, long size) throws QuotaExceededException, StorageException {
        long usedSpace = findAllSieveScriptsForUser(username).stream()
                .filter(sieveScript -> !sieveScript.getScriptName().equals(name.getValue()))
                .mapToLong(JPASieveScript::getScriptSize)
                .sum();

        QuotaSizeLimit quota = limitToUser(username);
        if (overQuotaAfterModification(usedSpace, size, quota)) {
            throw new QuotaExceededException();
        }
    }

    private QuotaSizeLimit limitToUser(Username username) {
        return postgresSieveQuotaDAO.getQuota(username)
            .filter(Optional::isPresent)
            .switchIfEmpty(postgresSieveQuotaDAO.getGlobalQuota())
            .block()
            .orElse(QuotaSizeLimit.unlimited());
    }

    private boolean overQuotaAfterModification(long usedSpace, long size, QuotaSizeLimit quota) {
        return QuotaSizeUsage.size(usedSpace)
                .add(size)
                .exceedLimit(quota);
    }

    @Override
    public void putScript(Username username, ScriptName name, ScriptContent content) {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            try {
                haveSpace(username, name, content.length());
                JPASieveScript jpaSieveScript = JPASieveScript.builder()
                        .username(username.asString())
                        .scriptName(name.getValue())
                        .scriptContent(content)
                        .build();
                entityManager.persist(jpaSieveScript);
            } catch (QuotaExceededException | StorageException e) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw e;
            }
        }).sneakyThrow(), throwStorageExceptionConsumer("Unable to put script for user " + username.asString()));
    }

    @Override
    public List<ScriptSummary> listScripts(Username username) {
        return findAllSieveScriptsForUser(username).stream()
                .map(JPASieveScript::toSummary)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Flux<ScriptSummary> listScriptsReactive(Username username) {
        return Mono.fromCallable(() -> listScripts(username)).flatMapMany(Flux::fromIterable);
    }

    private List<JPASieveScript> findAllSieveScriptsForUser(Username username) {
        return transactionRunner.runAndRetrieveResult(entityManager -> {
            List<JPASieveScript> sieveScripts = entityManager.createNamedQuery("findAllByUsername", JPASieveScript.class)
                    .setParameter("username", username.asString()).getResultList();
            return Optional.ofNullable(sieveScripts).orElse(ImmutableList.of());
        }, throwStorageException("Unable to list scripts for user " + username.asString()));
    }

    @Override
    public ZonedDateTime getActivationDateForActiveScript(Username username) throws ScriptNotFoundException {
        Optional<JPASieveScript> script = findActiveSieveScript(username);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + username.asString()));
        return activeSieveScript.getActivationDateTime().toZonedDateTime();
    }

    @Override
    public InputStream getActive(Username username) throws ScriptNotFoundException {
        Optional<JPASieveScript> script = findActiveSieveScript(username);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + username.asString()));
        return IOUtils.toInputStream(activeSieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findActiveSieveScript(Username username) {
        return transactionRunner.runAndRetrieveResult(
                Throwing.<EntityManager, Optional<JPASieveScript>>function(entityManager -> findActiveSieveScript(username, entityManager)).sneakyThrow(),
                throwStorageException("Unable to find active script for user " + username.asString()));
    }

    private Optional<JPASieveScript> findActiveSieveScript(Username username, EntityManager entityManager) {
        try {
            JPASieveScript activeSieveScript = entityManager.createNamedQuery("findActiveByUsername", JPASieveScript.class)
                    .setParameter("username", username.asString()).getSingleResult();
            return Optional.ofNullable(activeSieveScript);
        } catch (NoResultException e) {
            LOGGER.debug("Sieve script not found for user {}", username.asString());
            return Optional.empty();
        }
    }

    @Override
    public void setActive(Username username, ScriptName name) {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            try {
                if (SieveRepository.NO_SCRIPT_NAME.equals(name)) {
                    switchOffActiveScript(username, entityManager);
                } else {
                    setActiveScript(username, name, entityManager);
                }
            } catch (StorageException | ScriptNotFoundException e) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw e;
            }
        }).sneakyThrow(), throwStorageExceptionConsumer("Unable to set active script " + name.getValue() + " for user " + username.asString()));
    }

    private void switchOffActiveScript(Username username, EntityManager entityManager) throws StorageException {
        Optional<JPASieveScript> activeSieveScript = findActiveSieveScript(username, entityManager);
        activeSieveScript.ifPresent(JPASieveScript::deactivate);
    }

    private void setActiveScript(Username username, ScriptName name, EntityManager entityManager) throws StorageException, ScriptNotFoundException {
        JPASieveScript sieveScript = findSieveScript(username, name, entityManager)
                .orElseThrow(() -> new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + username.asString()));
        findActiveSieveScript(username, entityManager).ifPresent(JPASieveScript::deactivate);
        sieveScript.activate();
    }

    @Override
    public InputStream getScript(Username username, ScriptName name) throws ScriptNotFoundException {
        Optional<JPASieveScript> script = findSieveScript(username, name);
        JPASieveScript sieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + username.asString()));
        return IOUtils.toInputStream(sieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findSieveScript(Username username, ScriptName scriptName) {
        return transactionRunner.runAndRetrieveResult(entityManager -> findSieveScript(username, scriptName, entityManager),
                throwStorageException("Unable to find script " + scriptName.getValue() + " for user " + username.asString()));
    }

    private Optional<JPASieveScript> findSieveScript(Username username, ScriptName scriptName, EntityManager entityManager) {
        try {
            JPASieveScript sieveScript = entityManager.createNamedQuery("findSieveScript", JPASieveScript.class)
                    .setParameter("username", username.asString())
                    .setParameter("scriptName", scriptName.getValue()).getSingleResult();
            return Optional.ofNullable(sieveScript);
        } catch (NoResultException e) {
            LOGGER.debug("Sieve script not found for user {}", username.asString());
            return Optional.empty();
        }
    }

    @Override
    public void deleteScript(Username username, ScriptName name) {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            Optional<JPASieveScript> sieveScript = findSieveScript(username, name, entityManager);
            if (!sieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + username.asString());
            }
            JPASieveScript sieveScriptToRemove = sieveScript.get();
            if (sieveScriptToRemove.isActive()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new IsActiveException("Unable to delete active script " + name.getValue() + " for user " + username.asString());
            }
            entityManager.remove(sieveScriptToRemove);
        }).sneakyThrow(), throwStorageExceptionConsumer("Unable to delete script " + name.getValue() + " for user " + username.asString()));
    }

    @Override
    public void renameScript(Username username, ScriptName oldName, ScriptName newName) {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            Optional<JPASieveScript> sieveScript = findSieveScript(username, oldName, entityManager);
            if (!sieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new ScriptNotFoundException("Unable to find script " + oldName.getValue() + " for user " + username.asString());
            }

            Optional<JPASieveScript> duplicatedSieveScript = findSieveScript(username, newName, entityManager);
            if (duplicatedSieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new DuplicateException("Unable to rename script. Duplicate found " + newName.getValue() + " for user " + username.asString());
            }

            JPASieveScript sieveScriptToRename = sieveScript.get();
            sieveScriptToRename.renameTo(newName);
        }).sneakyThrow(), throwStorageExceptionConsumer("Unable to rename script " + oldName.getValue() + " for user " + username.asString()));
    }

    private void rollbackTransactionIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    @Override
    public boolean hasDefaultQuota() {
        return postgresSieveQuotaDAO.getGlobalQuota()
            .block()
            .isPresent();
    }

    @Override
    public QuotaSizeLimit getDefaultQuota() throws QuotaNotFoundException {
        return postgresSieveQuotaDAO.getGlobalQuota()
            .block()
            .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for default user"));
    }

    @Override
    public void setDefaultQuota(QuotaSizeLimit quota) {
        postgresSieveQuotaDAO.setGlobalQuota(quota)
            .block();
    }

    @Override
    public void removeQuota() {
        postgresSieveQuotaDAO.removeGlobalQuota()
            .block();
    }

    @Override
    public boolean hasQuota(Username username) {
        Mono<Boolean> hasUserQuota = postgresSieveQuotaDAO.getQuota(username).map(Optional::isPresent);
        Mono<Boolean> hasGlobalQuota = postgresSieveQuotaDAO.getGlobalQuota().map(Optional::isPresent);

        return hasUserQuota.zipWith(hasGlobalQuota, (a, b) -> a || b)
            .block();
    }

    @Override
    public QuotaSizeLimit getQuota(Username username) throws QuotaNotFoundException {
        return postgresSieveQuotaDAO.getQuota(username)
            .block()
            .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for user " + username.asString()));
    }

    @Override
    public void setQuota(Username username, QuotaSizeLimit quota) {
        postgresSieveQuotaDAO.setQuota(username, quota)
            .block();
    }

    @Override
    public void removeQuota(Username username) {
        postgresSieveQuotaDAO.removeQuota(username).block();
    }

    private <T> Function<PersistenceException, T> throwStorageException(String message) {
        return Throwing.<PersistenceException, T>function(e -> {
            throw new StorageException(message, e);
        }).sneakyThrow();
    }

    private Consumer<PersistenceException> throwStorageExceptionConsumer(String message) {
        return Throwing.<PersistenceException>consumer(e -> {
            throw new StorageException(message, e);
        }).sneakyThrow();
    }

    @Override
    public Mono<Void> resetSpaceUsedReactive(Username username, long spaceUsed) {
        return Mono.error(new UnsupportedOperationException());
    }
}