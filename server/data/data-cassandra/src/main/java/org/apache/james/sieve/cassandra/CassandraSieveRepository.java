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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
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
import org.apache.james.util.FunctionalUtils;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public ZonedDateTime getActivationDateForActiveScript(Username username) throws ScriptNotFoundException {
        return cassandraActiveScriptDAO.getActiveScriptInfo(username)
            .blockOptional()
            .orElseThrow(ScriptNotFoundException::new)
            .getActivationDate();
    }

    @Override
    public void haveSpace(Username username, ScriptName name, long newSize) throws QuotaExceededException {
        reThrowQuotaExceededException(() ->
            spaceThatWillBeUsedByNewScript(username, name, newSize)
                .flatMap(sizeDifference -> throwOnOverQuota(username, sizeDifference))
                .block());
    }

    private Mono<Void> throwOnOverQuota(Username username, Long sizeDifference) {
        Mono<Optional<QuotaSizeLimit>> userQuotaMono = cassandraSieveQuotaDAO.getQuota(username);
        Mono<Optional<QuotaSizeLimit>> globalQuotaMono = cassandraSieveQuotaDAO.getQuota();
        Mono<Long> spaceUsedMono = cassandraSieveQuotaDAO.spaceUsedBy(username);

        return limitToUse(userQuotaMono, globalQuotaMono).zipWith(spaceUsedMono)
            .flatMap(pair -> checkOverQuotaUponModification(sizeDifference, pair.getT2(), pair.getT1()));
    }

    private Mono<Void> checkOverQuotaUponModification(Long sizeDifference, Long spaceUsed, Optional<QuotaSizeLimit> limit) {
        try {
            new SieveQuota(spaceUsed, limit)
                .checkOverQuotaUponModification(sizeDifference);
            return Mono.empty();
        } catch (QuotaExceededException e) {
            return Mono.error(new RuntimeException(e));
        }
    }

    private Mono<Long> spaceThatWillBeUsedByNewScript(Username username, ScriptName name, long scriptSize) {
        return cassandraSieveDAO.getScript(username, name)
            .map(Script::getSize)
            .defaultIfEmpty(0L)
            .map(sizeOfStoredScript -> scriptSize - sizeOfStoredScript);
    }

    private Mono<Optional<QuotaSizeLimit>> limitToUse(Mono<Optional<QuotaSizeLimit>> userQuota, Mono<Optional<QuotaSizeLimit>> globalQuota) {
        return userQuota
            .filter(Optional::isPresent)
            .switchIfEmpty(globalQuota);
    }

    @Override
    public void putScript(Username username, ScriptName name, ScriptContent content) throws QuotaExceededException {
        Function<Long, Mono<Void>> updateAndInsert = spaceUsed -> Flux.merge(
                updateSpaceUsed(username, spaceUsed),
                cassandraSieveDAO.insertScript(username,
                        Script.builder()
                                .name(name)
                                .content(content)
                                .isActive(false)
                                .build()))
                .then();

        reThrowQuotaExceededException(() ->
            spaceThatWillBeUsedByNewScript(username, name, content.length())
                .flatMap(spaceUsed -> throwOnOverQuota(username, spaceUsed)
                        .thenEmpty(updateAndInsert.apply(spaceUsed)))
                .block());
    }

    private void reThrowQuotaExceededException(Runnable runnable) throws QuotaExceededException {
       try {
           runnable.run();
       } catch (RuntimeException e) {
           if (e.getCause() instanceof QuotaExceededException) {
               throw (QuotaExceededException) e.getCause();
           }
       }
    }

    private Mono<Void> updateSpaceUsed(Username username, long spaceUsed) {
        if (spaceUsed == 0) {
            return Mono.empty();
        }
        return cassandraSieveQuotaDAO.updateSpaceUsed(username, spaceUsed);
    }

    @Override
    public List<ScriptSummary> listScripts(Username username) {
        return cassandraSieveDAO.listScripts(username).collect(ImmutableList.toImmutableList()).block();
    }

    @Override
    public Flux<ScriptSummary> listScriptsReactive(Username username) {
        return cassandraSieveDAO.listScripts(username);
    }

    @Override
    public InputStream getActive(Username username) throws ScriptNotFoundException {
        return IOUtils.toInputStream(
            cassandraActiveScriptDAO.getActiveScriptInfo(username)
                .flatMap(activeScriptInfo -> cassandraSieveDAO.getScript(username, activeScriptInfo.getName()))
                .blockOptional()
                .orElseThrow(ScriptNotFoundException::new)
                .getContent()
                .getValue(), StandardCharsets.UTF_8);
    }

    @Override
    public void setActive(Username username, ScriptName name) throws ScriptNotFoundException {
        Mono<Boolean> activateNewScript =
            unActivateOldScript(username)
                .then(updateScriptActivation(username, name, true))
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(any -> cassandraActiveScriptDAO.activate(username, name).thenReturn(any));

        if (activateNewScript.blockOptional().isEmpty()) {
            throw new ScriptNotFoundException();
        }
    }

    private Mono<Void> unActivateOldScript(Username username) {
        return cassandraActiveScriptDAO.getActiveScriptInfo(username)
            .flatMap(activeScriptInfo -> updateScriptActivation(username, activeScriptInfo.getName(), false))
            .then();
    }

    private Mono<Boolean> updateScriptActivation(Username username, ScriptName scriptName, boolean active) {
        if (!scriptName.equals(SieveRepository.NO_SCRIPT_NAME)) {
            return cassandraSieveDAO.updateScriptActivation(username, scriptName, active);
        }
        return cassandraActiveScriptDAO.unActivate(username).thenReturn(true);
    }

    @Override
    public InputStream getScript(Username username, ScriptName name) throws ScriptNotFoundException {
        return  cassandraSieveDAO.getScript(username, name)
            .blockOptional()
            .map(script -> IOUtils.toInputStream(script.getContent().getValue(), StandardCharsets.UTF_8))
            .orElseThrow(ScriptNotFoundException::new);
    }

    @Override
    public void deleteScript(Username username, ScriptName name) throws ScriptNotFoundException, IsActiveException {
        ensureIsNotActive(username, name);
        if (!cassandraSieveDAO.deleteScriptInCassandra(username, name).defaultIfEmpty(false).block()) {
            throw new ScriptNotFoundException();
        }
    }

    private void ensureIsNotActive(Username username, ScriptName name) throws IsActiveException {
        Optional<ScriptName> activeName = cassandraActiveScriptDAO.getActiveScriptInfo(username).blockOptional().map(ActiveScriptInfo::getName);
        if (activeName.isPresent() && name.equals(activeName.get())) {
            throw new IsActiveException();
        }
    }

    @Override
    public void renameScript(Username username, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException {
        Mono<Script> oldScript = cassandraSieveDAO.getScript(username, oldName).cache();
        Mono<Boolean> newScriptExists = cassandraSieveDAO.getScript(username, newName).hasElement();

        oldScript.block();
        if (newScriptExists.block()) {
            throw new DuplicateException();
        }

        performScriptRename(username,
            newName,
            oldScript.blockOptional().orElseThrow(ScriptNotFoundException::new));
    }

    private void performScriptRename(Username username, ScriptName newName, Script oldScript) {
        Flux.merge(
            cassandraSieveDAO.insertScript(username,
                Script.builder()
                    .copyOf(oldScript)
                    .name(newName)
                    .build()),
            cassandraSieveDAO.deleteScriptInCassandra(username, oldScript.getName()),
            performActiveScriptRename(username, oldScript.getName(), newName))
            .then()
            .block();
    }

    private Mono<Void> performActiveScriptRename(Username username, ScriptName oldName, ScriptName newName) {
        return cassandraActiveScriptDAO.getActiveScriptInfo(username)
            .filter(activeScriptInfo -> activeScriptInfo.getName().equals(oldName))
            .flatMap(name -> cassandraActiveScriptDAO.activate(username, newName));
    }

    @Override
    public boolean hasDefaultQuota() {
        return cassandraSieveQuotaDAO.getQuota()
            .block()
            .isPresent();
    }

    @Override
    public QuotaSizeLimit getDefaultQuota() throws QuotaNotFoundException {
        return cassandraSieveQuotaDAO.getQuota()
            .block()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setDefaultQuota(QuotaSizeLimit quota) {
        cassandraSieveQuotaDAO.setQuota(quota).block();
    }

    @Override
    public void removeQuota() {
        cassandraSieveQuotaDAO.removeQuota().block();
    }

    @Override
    public boolean hasQuota(Username username) {
        Mono<Boolean> hasUserQuota = cassandraSieveQuotaDAO.getQuota(username).map(Optional::isPresent);
        Mono<Boolean> hasGlobalQuota = cassandraSieveQuotaDAO.getQuota().map(Optional::isPresent);

        return hasUserQuota.zipWith(hasGlobalQuota, (a, b) -> a || b)
            .block();
    }

    @Override
    public QuotaSizeLimit getQuota(Username username) throws QuotaNotFoundException {
        return cassandraSieveQuotaDAO.getQuota(username)
            .block()
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(Username username, QuotaSizeLimit quota) {
        cassandraSieveQuotaDAO.setQuota(username, quota).block();
    }

    @Override
    public void removeQuota(Username username) {
        cassandraSieveQuotaDAO.removeQuota(username).block();
    }

    @Override
    public Mono<Void> resetSpaceUsedReactive(Username username, long spaceUsed) {
        return cassandraSieveQuotaDAO.resetSpaceUsed(username, spaceUsed);
    }
}
