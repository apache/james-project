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

import static org.apache.james.backends.postgres.utils.PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.sieve.postgres.model.PostgresSieveScript;
import org.apache.james.sieve.postgres.model.PostgresSieveScriptId;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresSieveRepository implements SieveRepository {
    private final PostgresSieveQuotaDAO postgresSieveQuotaDAO;
    private final PostgresSieveScriptDAO postgresSieveScriptDAO;

    @Inject
    public PostgresSieveRepository(PostgresSieveQuotaDAO postgresSieveQuotaDAO,
                                   PostgresSieveScriptDAO postgresSieveScriptDAO) {
        this.postgresSieveQuotaDAO = postgresSieveQuotaDAO;
        this.postgresSieveScriptDAO = postgresSieveScriptDAO;
    }

    @Override
    public void haveSpace(Username username, ScriptName name, long size) throws QuotaExceededException {
        long sizeDifference = spaceThatWillBeUsedByNewScript(username, name, size).block();
        throwOnOverQuota(username, sizeDifference);
    }

    @Override
    public void putScript(Username username, ScriptName name, ScriptContent content) throws QuotaExceededException {
        long sizeDifference = spaceThatWillBeUsedByNewScript(username, name, content.length()).block();
        throwOnOverQuota(username, sizeDifference);
        postgresSieveScriptDAO.upsertScript(PostgresSieveScript.builder()
                .username(username.asString())
                .scriptName(name.getValue())
                .scriptContent(content.getValue())
                .scriptSize(content.length())
                .isActive(false)
                .id(PostgresSieveScriptId.generate())
                .build())
            .flatMap(upsertedScripts -> {
                if (upsertedScripts > 0) {
                    return updateSpaceUsed(username, sizeDifference);
                }
                return Mono.empty();
            })
            .block();
    }

    private Mono<Void> updateSpaceUsed(Username username, long spaceToUse) {
        if (spaceToUse == 0) {
            return Mono.empty();
        }
        return postgresSieveQuotaDAO.updateSpaceUsed(username, spaceToUse);
    }

    private Mono<Long> spaceThatWillBeUsedByNewScript(Username username, ScriptName name, long scriptSize) {
        return postgresSieveScriptDAO.getScriptSize(username, name)
            .defaultIfEmpty(0L)
            .map(sizeOfStoredScript -> scriptSize - sizeOfStoredScript);
    }

    private void throwOnOverQuota(Username username, Long sizeDifference) throws QuotaExceededException {
        long spaceUsed = postgresSieveQuotaDAO.spaceUsedBy(username).block();
        QuotaSizeLimit limit = limitToUser(username).block();

        if (QuotaSizeUsage.size(spaceUsed)
            .add(sizeDifference)
            .exceedLimit(limit)) {
            throw new QuotaExceededException();
        }
    }

    private Mono<QuotaSizeLimit> limitToUser(Username username) {
        return postgresSieveQuotaDAO.getQuota(username)
            .filter(Optional::isPresent)
            .switchIfEmpty(postgresSieveQuotaDAO.getGlobalQuota())
            .map(optional -> optional.orElse(QuotaSizeLimit.unlimited()));
    }

    @Override
    public List<ScriptSummary> listScripts(Username username) {
        return listScriptsReactive(username)
            .collectList()
            .block();
    }

    @Override
    public Flux<ScriptSummary> listScriptsReactive(Username username) {
        return postgresSieveScriptDAO.getScripts(username)
            .map(PostgresSieveScript::toScriptSummary);
    }

    @Override
    public ZonedDateTime getActivationDateForActiveScript(Username username) throws ScriptNotFoundException {
        return postgresSieveScriptDAO.getActiveScript(username)
            .blockOptional()
            .orElseThrow(ScriptNotFoundException::new)
            .getActivationDateTime()
            .toZonedDateTime();
    }

    @Override
    public InputStream getActive(Username username) throws ScriptNotFoundException {
        return IOUtils.toInputStream(postgresSieveScriptDAO.getActiveScript(username)
            .blockOptional()
            .orElseThrow(ScriptNotFoundException::new)
            .getScriptContent(), StandardCharsets.UTF_8);
    }

    @Override
    public void setActive(Username username, ScriptName name) throws ScriptNotFoundException {
        if (SieveRepository.NO_SCRIPT_NAME.equals(name)) {
            switchOffCurrentActiveScript(username);
        } else {
            throwOnScriptNonExistence(username, name);
            switchOffCurrentActiveScript(username);
            activateScript(username, name);
        }
    }

    private void throwOnScriptNonExistence(Username username, ScriptName name) throws ScriptNotFoundException {
        if (!postgresSieveScriptDAO.scriptExists(username, name).block()) {
            throw new ScriptNotFoundException();
        }
    }

    private void switchOffCurrentActiveScript(Username username) {
        postgresSieveScriptDAO.deactivateCurrentActiveScript(username).block();
    }

    private void activateScript(Username username, ScriptName scriptName) {
        postgresSieveScriptDAO.activateScript(username, scriptName).block();
    }

    @Override
    public InputStream getScript(Username username, ScriptName name) throws ScriptNotFoundException {
        return IOUtils.toInputStream(postgresSieveScriptDAO.getScript(username, name)
            .blockOptional()
            .orElseThrow(ScriptNotFoundException::new)
            .getScriptContent(), StandardCharsets.UTF_8);
    }

    @Override
    public void deleteScript(Username username, ScriptName name) throws ScriptNotFoundException, IsActiveException {
        boolean isActive = postgresSieveScriptDAO.getIsActive(username, name)
            .blockOptional()
            .orElseThrow(ScriptNotFoundException::new);

        if (isActive) {
            throw new IsActiveException();
        }

        postgresSieveScriptDAO.deleteScript(username, name).block();
    }

    @Override
    public void renameScript(Username username, ScriptName oldName, ScriptName newName) throws DuplicateException, ScriptNotFoundException {
        try {
            int renamedScripts = postgresSieveScriptDAO.renameScript(username, oldName, newName).block();
            if (renamedScripts == 0) {
                throw new ScriptNotFoundException();
            }
        } catch (Exception e) {
            if (UNIQUE_CONSTRAINT_VIOLATION_PREDICATE.test(e)) {
                throw new DuplicateException();
            }
            throw e;
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

    @Override
    public Mono<Void> resetSpaceUsedReactive(Username username, long spaceUsed) {
        return Mono.error(new UnsupportedOperationException());
    }
}