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
import java.util.function.Function;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
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
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class JPASieveRepository implements SieveRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPASieveRepository.class);
    private static final String DEFAULT_SIEVE_QUOTA_USERNAME = "default.quota";

    private final TransactionRunner transactionRunner;

    @Inject
    public JPASieveRepository(EntityManagerFactory entityManagerFactory) {
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
    }

    @Override
    public void haveSpace(User user, ScriptName name, long size) throws QuotaExceededException, StorageException {
        long usedSpace = findAllSieveScriptsForUser(user).stream()
                .filter(sieveScript -> !sieveScript.getScriptName().equals(name.getValue()))
                .mapToLong(JPASieveScript::getScriptSize)
                .sum();

        QuotaSize quota = limitToUser(user);
        if (overQuotaAfterModification(usedSpace, size, quota)) {
            throw new QuotaExceededException();
        }
    }

    private QuotaSize limitToUser(User user) throws StorageException {
        return OptionalUtils.orSuppliers(
                Throwing.supplier(() -> findQuotaForUser(user.asString())).sneakyThrow(),
                Throwing.supplier(() -> findQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME)).sneakyThrow())
                .map(JPASieveQuota::toQuotaSize)
                .orElse(QuotaSize.unlimited());
    }

    private boolean overQuotaAfterModification(long usedSpace, long size, QuotaSize quota) {
        return QuotaSize.size(usedSpace)
                .add(size)
                .isGreaterThan(quota);
    }

    @Override
    public void putScript(User user, ScriptName name, ScriptContent content) throws StorageException, QuotaExceededException {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            try {
                haveSpace(user, name, content.length());
                JPASieveScript jpaSieveScript = JPASieveScript.builder()
                        .username(user.asString())
                        .scriptName(name.getValue())
                        .scriptContent(content)
                        .build();
                entityManager.persist(jpaSieveScript);
            } catch (QuotaExceededException | StorageException e) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw e;
            }
        }).sneakyThrow(), throwStorageException("Unable to put script for user " + user.asString()));
    }

    @Override
    public List<ScriptSummary> listScripts(User user) throws StorageException {
        return findAllSieveScriptsForUser(user).stream()
                .map(JPASieveScript::toSummary)
                .collect(ImmutableList.toImmutableList());
    }

    private List<JPASieveScript> findAllSieveScriptsForUser(User user) throws StorageException {
        return transactionRunner.runAndRetrieveResult(entityManager -> {
            List<JPASieveScript> sieveScripts = entityManager.createNamedQuery("findAllByUsername", JPASieveScript.class)
                    .setParameter("username", user.asString()).getResultList();
            return Optional.ofNullable(sieveScripts).orElse(ImmutableList.of());
        }, throwStorageException("Unable to list scripts for user " + user.asString()));
    }

    @Override
    public ZonedDateTime getActivationDateForActiveScript(User user) throws StorageException, ScriptNotFoundException {
        Optional<JPASieveScript> script = findActiveSieveScript(user);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + user.asString()));
        return activeSieveScript.getActivationDateTime().toZonedDateTime();
    }

    @Override
    public InputStream getActive(User user) throws ScriptNotFoundException, StorageException {
        Optional<JPASieveScript> script = findActiveSieveScript(user);
        JPASieveScript activeSieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find active script for user " + user.asString()));
        return IOUtils.toInputStream(activeSieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findActiveSieveScript(User user) throws StorageException {
        return transactionRunner.runAndRetrieveResult(
                Throwing.<EntityManager, Optional<JPASieveScript>>function(entityManager -> findActiveSieveScript(user, entityManager)).sneakyThrow(),
                throwStorageException("Unable to find active script for user " + user.asString()));
    }

    private Optional<JPASieveScript> findActiveSieveScript(User user, EntityManager entityManager) throws StorageException {
        try {
            JPASieveScript activeSieveScript = entityManager.createNamedQuery("findActiveByUsername", JPASieveScript.class)
                    .setParameter("username", user.asString()).getSingleResult();
            return Optional.ofNullable(activeSieveScript);
        } catch (NoResultException e) {
            LOGGER.debug("Sieve script not found for user {}", user.asString());
            return Optional.empty();
        }
    }

    @Override
    public void setActive(User user, ScriptName name) throws ScriptNotFoundException, StorageException {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            try {
                if (SieveRepository.NO_SCRIPT_NAME.equals(name)) {
                    switchOffActiveScript(user, entityManager);
                } else {
                    setActiveScript(user, name, entityManager);
                }
            } catch (StorageException | ScriptNotFoundException e) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw e;
            }
        }).sneakyThrow(), throwStorageException("Unable to set active script " + name.getValue() + " for user " + user.asString()));
    }

    private void switchOffActiveScript(User user, EntityManager entityManager) throws StorageException {
        Optional<JPASieveScript> activeSieveScript = findActiveSieveScript(user, entityManager);
        activeSieveScript.ifPresent(JPASieveScript::deactivate);
    }

    private void setActiveScript(User user, ScriptName name, EntityManager entityManager) throws StorageException, ScriptNotFoundException {
        JPASieveScript sieveScript = findSieveScript(user, name, entityManager)
                .orElseThrow(() -> new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + user.asString()));
        findActiveSieveScript(user, entityManager).ifPresent(JPASieveScript::deactivate);
        sieveScript.activate();
    }

    @Override
    public InputStream getScript(User user, ScriptName name) throws ScriptNotFoundException, StorageException {
        Optional<JPASieveScript> script = findSieveScript(user, name);
        JPASieveScript sieveScript = script.orElseThrow(() -> new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + user.asString()));
        return IOUtils.toInputStream(sieveScript.getScriptContent(), StandardCharsets.UTF_8);
    }

    private Optional<JPASieveScript> findSieveScript(User user, ScriptName scriptName) throws StorageException {
        return transactionRunner.runAndRetrieveResult(entityManager -> findSieveScript(user, scriptName, entityManager),
                throwStorageException("Unable to find script " + scriptName.getValue() + " for user " + user.asString()));
    }

    private Optional<JPASieveScript> findSieveScript(User user, ScriptName scriptName, EntityManager entityManager) {
        try {
            JPASieveScript sieveScript = entityManager.createNamedQuery("findSieveScript", JPASieveScript.class)
                    .setParameter("username", user.asString())
                    .setParameter("scriptName", scriptName.getValue()).getSingleResult();
            return Optional.ofNullable(sieveScript);
        } catch (NoResultException e) {
            LOGGER.debug("Sieve script not found for user {}", user.asString());
            return Optional.empty();
        }
    }

    @Override
    public void deleteScript(User user, ScriptName name) throws ScriptNotFoundException, IsActiveException, StorageException {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            Optional<JPASieveScript> sieveScript = findSieveScript(user, name, entityManager);
            if (!sieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new ScriptNotFoundException("Unable to find script " + name.getValue() + " for user " + user.asString());
            }
            JPASieveScript sieveScriptToRemove = sieveScript.get();
            if (sieveScriptToRemove.isActive()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new IsActiveException("Unable to delete active script " + name.getValue() + " for user " + user.asString());
            }
            entityManager.remove(sieveScriptToRemove);
        }).sneakyThrow(), throwStorageException("Unable to delete script " + name.getValue() + " for user " + user.asString()));
    }

    @Override
    public void renameScript(User user, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException, StorageException {
        transactionRunner.runAndHandleException(Throwing.<EntityManager>consumer(entityManager -> {
            Optional<JPASieveScript> sieveScript = findSieveScript(user, oldName, entityManager);
            if (!sieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new ScriptNotFoundException("Unable to find script " + oldName.getValue() + " for user " + user.asString());
            }

            Optional<JPASieveScript> duplicatedSieveScript = findSieveScript(user, newName, entityManager);
            if (duplicatedSieveScript.isPresent()) {
                rollbackTransactionIfActive(entityManager.getTransaction());
                throw new DuplicateException("Unable to rename script. Duplicate found " + newName.getValue() + " for user " + user.asString());
            }

            JPASieveScript sieveScriptToRename = sieveScript.get();
            sieveScriptToRename.renameTo(newName);
        }).sneakyThrow(), throwStorageException("Unable to rename script " + oldName.getValue() + " for user " + user.asString()));
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
    public QuotaSize getDefaultQuota() throws QuotaNotFoundException, StorageException {
        JPASieveQuota jpaSieveQuota = findQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME)
                .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for default user"));
        return QuotaSize.size(jpaSieveQuota.getSize());
    }

    @Override
    public void setDefaultQuota(QuotaSize quota) throws StorageException {
        setQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME, quota);
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException, StorageException {
        removeQuotaForUser(DEFAULT_SIEVE_QUOTA_USERNAME);
    }

    @Override
    public boolean hasQuota(User user) throws StorageException {
        Optional<JPASieveQuota> quotaForUser = findQuotaForUser(user.asString());
        return quotaForUser.isPresent();
    }

    @Override
    public QuotaSize getQuota(User user) throws QuotaNotFoundException, StorageException {
        JPASieveQuota jpaSieveQuota = findQuotaForUser(user.asString())
                .orElseThrow(() -> new QuotaNotFoundException("Unable to find quota for user " + user.asString()));
        return QuotaSize.size(jpaSieveQuota.getSize());
    }

    @Override
    public void setQuota(User user, QuotaSize quota) throws StorageException {
        setQuotaForUser(user.asString(), quota);
    }

    @Override
    public void removeQuota(User user) throws QuotaNotFoundException, StorageException {
        removeQuotaForUser(user.asString());
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

    private Optional<JPASieveQuota> findQuotaForUser(String username, EntityManager entityManager) {
        try {
            JPASieveQuota sieveQuota = entityManager.createNamedQuery("findByUsername", JPASieveQuota.class)
                    .setParameter("username", username).getSingleResult();
            return Optional.of(sieveQuota);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private void setQuotaForUser(String username, QuotaSize quota) throws StorageException {
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
        }), throwStorageException("Unable to set quota for user " + username));
    }

    private void removeQuotaForUser(String username) throws StorageException {
        transactionRunner.runAndHandleException(Throwing.consumer(entityManager -> {
            Optional<JPASieveQuota> quotaForUser = findQuotaForUser(username, entityManager);
            quotaForUser.ifPresent(entityManager::remove);
        }), throwStorageException("Unable to remove quota for user " + username));
    }
}