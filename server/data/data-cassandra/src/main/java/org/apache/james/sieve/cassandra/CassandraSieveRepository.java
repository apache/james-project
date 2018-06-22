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

package org.apache.james.sieve.cassandra;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.sieve.cassandra.model.ActiveScriptInfo;
import org.apache.james.sieve.cassandra.model.Script;
import org.apache.james.sieve.cassandra.model.SieveQuota;
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
import org.apache.james.util.CompletableFutureUtil;
import org.joda.time.DateTime;

public class CassandraSieveRepository implements SieveRepository {

    private final CassandraSieveDAO cassandraSieveDAO;
    private final CassandraSieveQuotaDAO cassandraSieveQuotaDAO;
    private final CassandraActiveScriptDAO cassandraActiveScriptDAO;

    @Inject
    public CassandraSieveRepository(CassandraSieveDAO cassandraSieveDAO, CassandraSieveQuotaDAO cassandraSieveQuotaDAO, CassandraActiveScriptDAO cassandraActiveScriptDAO) {
        this.cassandraSieveDAO = cassandraSieveDAO;
        this.cassandraSieveQuotaDAO = cassandraSieveQuotaDAO;
        this.cassandraActiveScriptDAO = cassandraActiveScriptDAO;
    }

    @Override
    public DateTime getActivationDateForActiveScript(User user) throws StorageException, ScriptNotFoundException {
        return cassandraActiveScriptDAO.getActiveSctiptInfo(user).join()
            .orElseThrow(ScriptNotFoundException::new)
            .getActivationDate();
    }

    @Override
    public void haveSpace(User user, ScriptName name, long newSize) throws QuotaExceededException, StorageException {
        throwOnOverQuota(user, spaceThatWillBeUsedByNewScript(user, name, newSize));
    }

    private void throwOnOverQuota(User user, CompletableFuture<Long> sizeDifference) throws QuotaExceededException, StorageException {
        CompletableFuture<Optional<QuotaSize>> userQuotaFuture = cassandraSieveQuotaDAO.getQuota(user);
        CompletableFuture<Optional<QuotaSize>> globalQuotaFuture = cassandraSieveQuotaDAO.getQuota();
        CompletableFuture<Long> spaceUsedFuture = cassandraSieveQuotaDAO.spaceUsedBy(user);

        new SieveQuota(spaceUsedFuture.join(), limitToUse(userQuotaFuture, globalQuotaFuture))
            .checkOverQuotaUponModification(sizeDifference.join());
    }

    public CompletableFuture<Long> spaceThatWillBeUsedByNewScript(User user, ScriptName name, long scriptSize) {
        return cassandraSieveDAO.getScript(user, name)
            .thenApply(optional -> optional.map(Script::getSize).orElse(0L))
            .thenApply(sizeOfStoredScript -> scriptSize - sizeOfStoredScript);
    }

    private Optional<QuotaSize> limitToUse(CompletableFuture<Optional<QuotaSize>> userQuota, CompletableFuture<Optional<QuotaSize>> globalQuota) {
        if (userQuota.join().isPresent()) {
            return userQuota.join();
        }
        return globalQuota.join();
    }

    @Override
    public void putScript(User user, ScriptName name, ScriptContent content) throws QuotaExceededException, StorageException {
        CompletableFuture<Long> spaceUsed = spaceThatWillBeUsedByNewScript(user, name, content.length());
        throwOnOverQuota(user, spaceUsed);

        CompletableFuture.allOf(
            updateSpaceUsed(user, spaceUsed.join()),
            cassandraSieveDAO.insertScript(user,
                Script.builder()
                    .name(name)
                    .content(content)
                    .isActive(false)
                    .build()))
            .join();
    }

    public CompletableFuture<Void> updateSpaceUsed(User user, long spaceUsed) {
        if (spaceUsed == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return cassandraSieveQuotaDAO.updateSpaceUsed(user, spaceUsed);
    }

    @Override
    public List<ScriptSummary> listScripts(User user) {
        return cassandraSieveDAO.listScripts(user).join();
    }

    @Override
    public InputStream getActive(User user) throws ScriptNotFoundException {
        return IOUtils.toInputStream(
            cassandraActiveScriptDAO.getActiveSctiptInfo(user)
                .thenCompose(optionalActiveName -> optionalActiveName
                    .map(activeScriptInfo -> cassandraSieveDAO.getScript(user, activeScriptInfo.getName()))
                    .orElse(CompletableFuture.completedFuture(Optional.empty())))
                .join()
                .orElseThrow(ScriptNotFoundException::new)
                .getContent()
                .getValue(), StandardCharsets.UTF_8);
    }

    @Override
    public void setActive(User user, ScriptName name) throws ScriptNotFoundException {
        CompletableFuture<Boolean> activateNewScript =
            unactivateOldScript(user)
                .thenCompose(any -> updateScriptActivation(user, name, true)
                    .thenCompose(CompletableFutureUtil.composeIfTrue(
                        () -> cassandraActiveScriptDAO.activate(user, name))));

        if (!activateNewScript.join()) {
            throw new ScriptNotFoundException();
        }
    }

    private CompletableFuture<Void> unactivateOldScript(User user) {
        return cassandraActiveScriptDAO.getActiveSctiptInfo(user)
            .thenCompose(scriptNameOptional -> scriptNameOptional
                .map(activeScriptInfo -> updateScriptActivation(user, activeScriptInfo.getName(), false)
                    .<Void>thenApply(any -> null))
                .orElse(CompletableFuture.completedFuture(null)));
    }

    private CompletableFuture<Boolean> updateScriptActivation(User user, ScriptName scriptName, boolean active) {
        if (!scriptName.equals(SieveRepository.NO_SCRIPT_NAME)) {
            return cassandraSieveDAO.updateScriptActivation(user, scriptName, active);
        }
        return cassandraActiveScriptDAO.unactivate(user).thenApply(any -> true);
    }

    @Override
    public InputStream getScript(User user, ScriptName name) throws ScriptNotFoundException {
        return  cassandraSieveDAO.getScript(user, name)
            .join()
            .map(script -> IOUtils.toInputStream(script.getContent().getValue(), StandardCharsets.UTF_8))
            .orElseThrow(ScriptNotFoundException::new);
    }

    @Override
    public void deleteScript(User user, ScriptName name) throws ScriptNotFoundException, IsActiveException {
        ensureIsNotActive(user, name);
        if (!cassandraSieveDAO.deleteScriptInCassandra(user, name).join()) {
            throw new ScriptNotFoundException();
        }
    }

    private void ensureIsNotActive(User user, ScriptName name) throws IsActiveException {
        Optional<ScriptName> activeName = cassandraActiveScriptDAO.getActiveSctiptInfo(user).join().map(ActiveScriptInfo::getName);
        if (activeName.isPresent() && name.equals(activeName.get())) {
            throw new IsActiveException();
        }
    }

    @Override
    public void renameScript(User user, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException {
        CompletableFuture<Boolean> scriptExistsFuture = cassandraSieveDAO.getScript(user, newName)
            .thenApply(Optional::isPresent);
        CompletableFuture<Optional<Script>> oldScriptFuture = cassandraSieveDAO.getScript(user, oldName);

        oldScriptFuture.join();
        if (scriptExistsFuture.join()) {
            throw new DuplicateException();
        }

        performScriptRename(user,
            newName,
            oldScriptFuture.join().orElseThrow(ScriptNotFoundException::new));
    }

    private void performScriptRename(User user, ScriptName newName, Script oldScript) {
        CompletableFuture.allOf(
            cassandraSieveDAO.insertScript(user,
                Script.builder()
                    .copyOf(oldScript)
                    .name(newName)
                    .build()),
            cassandraSieveDAO.deleteScriptInCassandra(user, oldScript.getName()),
            performActiveScriptRename(user, oldScript.getName(), newName))
            .join();
    }

    private CompletableFuture<Void> performActiveScriptRename(User user, ScriptName oldName, ScriptName newName) {
        return cassandraActiveScriptDAO.getActiveSctiptInfo(user)
            .thenCompose(optionalActivationInfo -> optionalActivationInfo
                .filter(activeScriptInfo -> activeScriptInfo.getName().equals(oldName))
                .map(name -> cassandraActiveScriptDAO.activate(user, newName))
                .orElse(CompletableFuture.completedFuture(null)));
    }

    @Override
    public boolean hasDefaultQuota() {
        return cassandraSieveQuotaDAO.getQuota()
            .join()
            .isPresent();
    }

    @Override
    public QuotaSize getDefaultQuota() throws QuotaNotFoundException {
        return cassandraSieveQuotaDAO.getQuota()
            .join()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setDefaultQuota(QuotaSize quota) {
        cassandraSieveQuotaDAO.setQuota(quota).join();
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException {
        cassandraSieveQuotaDAO.removeQuota().join();
    }

    @Override
    public boolean hasQuota(User user) {
        return CompletableFutureUtil.combine(
            cassandraSieveQuotaDAO.getQuota(user).thenApply(Optional::isPresent),
            cassandraSieveQuotaDAO.getQuota().thenApply(Optional::isPresent),
            (b1, b2) -> b1 || b2)
            .join();
    }

    @Override
    public QuotaSize getQuota(User user) throws QuotaNotFoundException {
        return cassandraSieveQuotaDAO.getQuota(user)
            .join()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(User user, QuotaSize quota) {
        cassandraSieveQuotaDAO.setQuota(user, quota).join();
    }

    @Override
    public void removeQuota(User user) throws QuotaNotFoundException {
        cassandraSieveQuotaDAO.removeQuota(user).join();
    }

}
