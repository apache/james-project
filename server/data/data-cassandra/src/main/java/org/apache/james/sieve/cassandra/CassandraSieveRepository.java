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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.sieve.cassandra.model.ScriptContentAndActivation;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieve.cassandra.model.SieveQuota;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.joda.time.DateTime;

public class CassandraSieveRepository implements SieveRepository {

    private final CassandraSieveDAO cassandraSieveDAO;

    @Inject
    public CassandraSieveRepository(CassandraSieveDAO cassandraSieveDAO) {
        this.cassandraSieveDAO = cassandraSieveDAO;
    }

    @Override
    public DateTime getActivationDateForActiveScript(String user) throws StorageException, ScriptNotFoundException {
        return cassandraSieveDAO.getActiveScriptActivationDate(user)
            .join()
            .orElseThrow(ScriptNotFoundException::new);
    }

    @Override
    public void haveSpace(String user, String name, long newSize) throws QuotaExceededException, StorageException {
        throwOnOverQuota(user, spaceThatWillBeUsedByNewScript(user, name, newSize));
    }

    private void throwOnOverQuota(String user, CompletableFuture<Long> sizeDifference) throws QuotaExceededException, StorageException {
        CompletableFuture<Optional<Long>> userQuotaFuture = cassandraSieveDAO.getQuota(user);
        CompletableFuture<Optional<Long>> globalQuotaFuture = cassandraSieveDAO.getQuota();
        CompletableFuture<Long> spaceUsedFuture = cassandraSieveDAO.spaceUsedBy(user);

        new SieveQuota(spaceUsedFuture.join(), limitToUse(userQuotaFuture, globalQuotaFuture)).checkOverQuotaUponModification(sizeDifference.join());
    }

    public CompletableFuture<Long> spaceThatWillBeUsedByNewScript(String user, String name, long scriptSize) {
        return cassandraSieveDAO.getScriptSize(user, name)
            .thenApply(sizeOfStoredScript -> scriptSize - sizeOfStoredScript.orElse(0L));
    }

    private Optional<Long> limitToUse(CompletableFuture<Optional<Long>> userQuota, CompletableFuture<Optional<Long>> globalQuota) {
        if (userQuota.join().isPresent()) {
            return userQuota.join();
        }
        return globalQuota.join();
    }

    @Override
    public void putScript(String user, String name, String content) throws QuotaExceededException, StorageException {
        CompletableFuture<Long> spaceUsed = spaceThatWillBeUsedByNewScript(user, name, content.length());
        throwOnOverQuota(user, spaceUsed);

        CompletableFuture.allOf(
            updateSpaceUsed(user, spaceUsed.join()),
            cassandraSieveDAO.insertScript(user, name, content, false))
            .join();
    }

    public CompletableFuture<Void> updateSpaceUsed(String user, long spaceUsed) {
        if (spaceUsed == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return cassandraSieveDAO.updateSpaceUsed(user, spaceUsed);
    }

    @Override
    public List<ScriptSummary> listScripts(String user) {
        return cassandraSieveDAO.listScripts(user).join();
    }

    @Override
    public InputStream getActive(String user) throws ScriptNotFoundException {
        return IOUtils.toInputStream(
            cassandraSieveDAO.getActive(user)
                .join()
                .orElseThrow(ScriptNotFoundException::new));
    }

    @Override
    public void setActive(String user, String name) throws ScriptNotFoundException {
        CompletableFuture<Void> unactivateOldScriptFuture = unactivateOldScript(user);
        CompletableFuture<Boolean> activateNewScript = updateScriptActivation(user, name, true);

        unactivateOldScriptFuture.join();
        if (!activateNewScript.join()) {
            throw new ScriptNotFoundException();
        }
    }

    private CompletableFuture<Void> unactivateOldScript(String user) {
        return cassandraSieveDAO.getActiveName(user)
            .thenCompose(scriptNameOptional -> scriptNameOptional
                .map(scriptName -> updateScriptActivation(user, scriptName, false)
                    .<Void>thenApply(any -> null))
                .orElse(CompletableFuture.completedFuture(null)));
    }

    private CompletableFuture<Boolean> updateScriptActivation(String user, String scriptName, boolean active) {
        if (!scriptName.equals(SieveRepository.NO_SCRIPT_NAME)) {
            return cassandraSieveDAO.updateScriptActivation(user, scriptName, active);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public InputStream getScript(String user, String name) throws ScriptNotFoundException {
        return  cassandraSieveDAO.getScriptContent(user, name)
            .join()
            .map(IOUtils::toInputStream)
            .orElseThrow(ScriptNotFoundException::new);
    }

    @Override
    public void deleteScript(String user, String name) throws ScriptNotFoundException, IsActiveException {
        ensureIsNotActive(user, name);
        if (!cassandraSieveDAO.deleteScriptInCassandra(user, name).join()) {
            throw new ScriptNotFoundException();
        }
    }

    private void ensureIsNotActive(String user, String name) throws IsActiveException {
        Optional<String> activeName = cassandraSieveDAO.getActiveName(user).join();
        if (activeName.isPresent() && name.equals(activeName.get())) {
            throw new IsActiveException();
        }
    }

    @Override
    public void renameScript(String user, String oldName, String newName) throws ScriptNotFoundException, DuplicateException {
        CompletableFuture<Boolean> scriptExistsFuture = cassandraSieveDAO.scriptExists(user, newName);
        CompletableFuture<Optional<ScriptContentAndActivation>> oldScriptFuture = cassandraSieveDAO.getScriptContentAndActivation(user, oldName);

        oldScriptFuture.join();
        if (scriptExistsFuture.join()) {
            throw new DuplicateException();
        }

        performScriptRename(user,
            oldName,
            newName,
            oldScriptFuture.join().orElseThrow(ScriptNotFoundException::new));
    }

    private void performScriptRename(String user, String oldName, String newName, ScriptContentAndActivation oldScript) {
        CompletableFuture.allOf(
            cassandraSieveDAO.insertScript(user, newName, oldScript.getContent(), oldScript.isActive()),
            cassandraSieveDAO.deleteScriptInCassandra(user, oldName))
            .join();
    }

    @Override
    public boolean hasQuota() {
        return cassandraSieveDAO.getQuota()
            .join()
            .isPresent();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException {
        return cassandraSieveDAO.getQuota()
            .join()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(long quota) {
        cassandraSieveDAO.setQuota(quota).join();
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException {
        if (!cassandraSieveDAO.removeQuota().join()) {
            throw new QuotaNotFoundException();
        }
    }

    @Override
    public boolean hasQuota(String user) {
        CompletableFuture<Boolean> userQuotaIsPresent = cassandraSieveDAO.getQuota(user).thenApply(Optional::isPresent);
        CompletableFuture<Boolean> globalQuotaIsPresent = cassandraSieveDAO.getQuota().thenApply(Optional::isPresent);
        CompletableFuture.allOf(userQuotaIsPresent, globalQuotaIsPresent).join();

        return userQuotaIsPresent.join() || globalQuotaIsPresent.join();
    }

    @Override
    public long getQuota(String user) throws QuotaNotFoundException {
        return cassandraSieveDAO.getQuota(user)
            .join()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(String user, long quota) {
        cassandraSieveDAO.setQuota(user, quota).join();
    }

    @Override
    public void removeQuota(String user) throws QuotaNotFoundException {
        if (!cassandraSieveDAO.removeQuota(user).join()) {
            throw new QuotaNotFoundException();
        }
    }

}
