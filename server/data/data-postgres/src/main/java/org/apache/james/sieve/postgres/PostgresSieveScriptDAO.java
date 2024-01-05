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

import static org.apache.james.backends.postgres.utils.PoolPostgresExecutor.POOL_INJECT_NAME;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.ACTIVATION_DATE_TIME;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.IS_ACTIVE;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.SCRIPT_CONTENT;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.SCRIPT_ID;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.SCRIPT_NAME;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.SCRIPT_SIZE;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.TABLE_NAME;
import static org.apache.james.sieve.postgres.PostgresSieveModule.PostgresSieveScriptTable.USERNAME;

import java.time.OffsetDateTime;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.sieve.postgres.model.PostgresSieveScript;
import org.apache.james.sieve.postgres.model.PostgresSieveScriptId;
import org.apache.james.sieverepository.api.ScriptName;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresSieveScriptDAO {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresSieveScriptDAO(@Named(POOL_INJECT_NAME) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Long> upsertScript(PostgresSieveScript sieveScript) {
        return postgresExecutor.executeReturnAffectedRowsCount(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(SCRIPT_ID, sieveScript.getId().getValue())
            .set(USERNAME, sieveScript.getUsername())
            .set(SCRIPT_NAME, sieveScript.getScriptName())
            .set(SCRIPT_SIZE, sieveScript.getScriptSize())
            .set(SCRIPT_CONTENT, sieveScript.getScriptContent())
            .set(IS_ACTIVE, sieveScript.isActive())
            .set(ACTIVATION_DATE_TIME, sieveScript.getActivationDateTime())
            .onConflict(USERNAME, SCRIPT_NAME)
            .doUpdate()
            .set(SCRIPT_SIZE, sieveScript.getScriptSize())
            .set(SCRIPT_CONTENT, sieveScript.getScriptContent())
            .set(IS_ACTIVE, sieveScript.isActive())
            .set(ACTIVATION_DATE_TIME, sieveScript.getActivationDateTime())));
    }

    public Mono<PostgresSieveScript> getScript(Username username, ScriptName scriptName) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()),
                SCRIPT_NAME.eq(scriptName.getValue()))))
            .map(recordToPostgresSieveScript());
    }

    public Mono<Long> getScriptSize(Username username, ScriptName scriptName) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(SCRIPT_SIZE)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()),
                    SCRIPT_NAME.eq(scriptName.getValue()))))
            .map(record -> record.get(SCRIPT_SIZE));
    }

    public Mono<Boolean> getIsActive(Username username, ScriptName scriptName) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(IS_ACTIVE)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()),
                    SCRIPT_NAME.eq(scriptName.getValue()))))
            .map(record -> record.get(IS_ACTIVE));
    }

    public Mono<Boolean> scriptExists(Username username, ScriptName scriptName) {
        return postgresExecutor.executeCount(dslContext -> Mono.from(dslContext.selectCount()
            .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()),
                    SCRIPT_NAME.eq(scriptName.getValue()))))
            .map(count -> count > 0);
    }

    public Flux<PostgresSieveScript> getScripts(Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .map(recordToPostgresSieveScript());
    }

    public Mono<PostgresSieveScript> getActiveScript(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(username.asString()),
                    IS_ACTIVE.eq(true))))
            .map(recordToPostgresSieveScript());
    }

    public Mono<Void> activateScript(Username username, ScriptName scriptName) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(IS_ACTIVE, true)
            .set(ACTIVATION_DATE_TIME, OffsetDateTime.now())
            .where(USERNAME.eq(username.asString()),
                SCRIPT_NAME.eq(scriptName.getValue()))));
    }

    public Mono<Void> deactivateCurrentActiveScript(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(IS_ACTIVE, false)
            .where(USERNAME.eq(username.asString()),
                IS_ACTIVE.eq(true))));
    }

    public Mono<Long> renameScript(Username username, ScriptName oldName, ScriptName newName) {
        return postgresExecutor.executeReturnAffectedRowsCount(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(SCRIPT_NAME, newName.getValue())
            .where(USERNAME.eq(username.asString()),
                SCRIPT_NAME.eq(oldName.getValue()))));
    }

    public Mono<Void> deleteScript(Username username, ScriptName scriptName) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()),
                SCRIPT_NAME.eq(scriptName.getValue()))));
    }

    private Function<Record, PostgresSieveScript> recordToPostgresSieveScript() {
        return record -> PostgresSieveScript.builder()
            .username(record.get(USERNAME))
            .scriptName(record.get(SCRIPT_NAME))
            .scriptContent(record.get(SCRIPT_CONTENT))
            .scriptSize(record.get(SCRIPT_SIZE))
            .isActive(record.get(IS_ACTIVE))
            .activationDateTime(record.get(ACTIVATION_DATE_TIME))
            .id(new PostgresSieveScriptId(record.get(SCRIPT_ID)))
            .build();
    }
}
