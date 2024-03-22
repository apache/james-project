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

package org.apache.james.sieve.jpa;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.sieve.jpa.model.JPASieveQuota;
import org.apache.james.sieve.jpa.model.JPASieveScript;
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

public class JPASieveRepository implements SieveRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPASieveRepository.class);
    private static final String DEFAULT_SIEVE_QUOTA_USERNAME = "default.quota";

    private final TransactionRunner transactionRunner;

    @Inject
    public JPASieveRepository(EntityManagerFactory entityManagerFactory) {
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
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

    private QuotaSizeLimit limitToUser(Username username) throws StorageException {
        return findQuotaForUser(username.asString())
            .or(Throwing.supplier(() -> findQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME)).sneakyThrow())
            .map(JPASieveQuota::toQuotaSize)
            .orElse(QuotaSizeLimit.unlimited());
    }

    private boolean overQuotaAfterModification(long usedSpace, long size, QuotaSizeLimit quota) {
        return QuotaSizeUsage.size(usedSpace)
                .add(size)
                .exceedLimit(quota);
    }

    @Override
    public void putScript(Username username, ScriptName name, ScriptContent content) throws StorageException, QuotaExceededException {
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
    public List<ScriptSummary> listScripts(Username username) throws StorageException {
        return findAllSieveScriptsForUser(username).stream()
                .map(JPASieveScript::toSummary)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Flux<ScriptSummary> listScriptsReactive(Username username) {
        return Mono.fromCallable(() -> listScripts(username)).flatMapMany(Flux::fromIterable);
    }

    private List<JPASieveScript> findAllSieveScriptsForUser(Username username) throws StorageException {
        return transactionRunner.runAndRetrieveResult(entityManager -> {
            List<JPASieveScript> sieveScripts = entityManager.createNamedQuery("findAllByUsername", JPASieveScript.class)
                    .setParameter("username", username.asString()).getResultList();
            return Optional.ofNullable(sieveScripts).orElse(ImmutableList.of());
        }, throwStorageException("Unable to list scripts for user " + username.asString()));
    }

    @Override
    public ZonedDateTime getActivationDateForActiveScript(Username username) throws StorageException, ScriptNotFoundException {
        Optional<JPASieveScript> script = findActiveSieveScript(username);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + username.asString()));
        return activeSieveScript.getActivationDateTime().toZonedDateTime();
    }

    @Override
    public InputStream getActive(Username username) throws ScriptNotFoundException, StorageException {
        Optional<JPASieveScript> script = findActiveSieveScript(username);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + username.asString()));
        return IOUtils.toInputStream(activeSieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findActiveSieveScript(Username username) throws StorageException {
        return transactionRunner.runAndRetrieveResult(
                Throwing.<EntityManager, Optional<JPASieveScript>>function(entityManager -> findActiveSieveScript(username, entityManager)).sneakyThrow(),
                throwStorageException("Unable to find active script for user " + username.asString()));
    }

    private Optional<JPASieveScript> findActiveSieveScript(Username username, EntityManager entityManager) throws StorageException {
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
    public void setActive(Username username, ScriptName name) throws ScriptNotFoundException, StorageException {
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
    public InputStream getScript(Username username, ScriptName name) throws ScriptNotFoundException, StorageException {
        Optional<JPASieveScript> script = findSieveScript(username, name);
        JPASieveScript sieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + username.asString()));
        return IOUtils.toInputStream(sieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findSieveScript(Username username, ScriptName scriptName) throws StorageException {
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
    public void deleteScript(Username username, ScriptName name) throws ScriptNotFoundException, IsActiveException, StorageException {
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
    public void renameScript(Username username, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException, StorageException {
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
    public boolean hasDefaultQuota() throws StorageException {
        Optional<JPASieveQuota> defaultQuota = findQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME);
        return defaultQuota.isPresent();
    }

    @Override
    public QuotaSizeLimit getDefaultQuota() throws QuotaNotFoundException, StorageException {
        JPASieveQuota jpaSieveQuota = findQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME)
                .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for default user"));
        return QuotaSizeLimit.size(jpaSieveQuota.getSize());
    }

    @Override
    public void setDefaultQuota(QuotaSizeLimit quota) throws StorageException {
        setQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME, quota);
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException, StorageException {
        removeQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME);
    }

    @Override
    public boolean hasQuota(Username username) throws StorageException {
        Optional<JPASieveQuota> quotaForUser = findQuotaForUser(username.asString());
        return quotaForUser.isPresent();
    }

    @Override
    public QuotaSizeLimit getQuota(Username username) throws QuotaNotFoundException, StorageException {
        JPASieveQuota jpaSieveQuota = findQuotaForUser(username.asString())
                .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for user " + username.asString()));
        return QuotaSizeLimit.size(jpaSieveQuota.getSize());
    }

    @Override
    public void setQuota(Username username, QuotaSizeLimit quota) throws StorageException {
        setQuotaForUser(username.asString(), quota);
    }

    @Override
    public void removeQuota(Username username) throws QuotaNotFoundException, StorageException {
        removeQuotaForUser(username.asString());
    }

    private Optional<JPASieveQuota> findQuotaForUser(String username) throws StorageException {
        return transactionRunner.runAndRetrieveResult(entityManager -> findQuotaForUser(username, entityManager),
                throwStorageException("Unable to find quota for user " + username));
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

    private Optional<JPASieveQuota> findQuotaForUser(String username, EntityManager entityManager) {
        try {
            JPASieveQuota sieveQuota = entityManager.createNamedQuery("findByUsername", JPASieveQuota.class)
                    .setParameter("username", username).getSingleResult();
            return Optional.of(sieveQuota);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private void setQuotaForUser(String username, QuotaSizeLimit quota) throws StorageException {
        transactionRunner.runAndHandleException(Throwing.consumer(entityManager -> {
            Optional<JPASieveQuota> sieveQuota = findQuotaForUser(username, entityManager);
            if (sieveQuota.isPresent()) {
                JPASieveQuota jpaSieveQuota = sieveQuota.get();
                jpaSieveQuota.setSize(quota);
                entityManager.merge(jpaSieveQuota);
            } else {
                JPASieveQuota jpaSieveQuota = new JPASieveQuota(username, quota.asLong());
                entityManager.persist(jpaSieveQuota);
            }
        }), throwStorageExceptionConsumer("Unable to set quota for user " + username));
    }

    private void removeQuotaForUser(String username) throws StorageException {
        transactionRunner.runAndHandleException(Throwing.consumer(entityManager -> {
            Optional<JPASieveQuota> quotaForUser = findQuotaForUser(username, entityManager);
            quotaForUser.ifPresent(entityManager::remove);
        }), throwStorageExceptionConsumer("Unable to remove quota for user " + username));
    }

    @Override
    public Mono<Void> resetSpaceUsedReactive(Username username, long spaceUsed) {
        return Mono.error(new UnsupportedOperationException());
    }
}