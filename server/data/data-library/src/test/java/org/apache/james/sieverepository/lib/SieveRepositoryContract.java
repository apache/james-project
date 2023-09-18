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
package org.apache.james.sieverepository.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.junit.jupiter.api.Test;

public interface SieveRepositoryContract {

    Username USERNAME = Username.of("test");
    ScriptName SCRIPT_NAME = new ScriptName("script");
    ScriptContent SCRIPT_CONTENT = new ScriptContent("Hello World");

    ScriptName OTHER_SCRIPT_NAME = new ScriptName("other_script");
    ScriptContent OTHER_SCRIPT_CONTENT = new ScriptContent("Other script content");
    QuotaSizeLimit DEFAULT_QUOTA = QuotaSizeLimit.size(Long.MAX_VALUE - 1L);
    QuotaSizeLimit USER_QUOTA = QuotaSizeLimit.size(Long.MAX_VALUE / 2);

    SieveRepository sieveRepository();

    @Test
    default void getScriptShouldThrowIfUnableToFindScript() {
        assertThatThrownBy(() -> sieveRepository().getScript(USERNAME, SCRIPT_NAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void getScriptShouldReturnCorrectContent() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(getScriptContent(sieveRepository().getScript(USERNAME, SCRIPT_NAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    default void getActivationDateForActiveScriptShouldReturnNonNullAndNonZeroResult() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        assertThat(sieveRepository().getActivationDateForActiveScript(USERNAME)).isNotNull();
        assertThat(sieveRepository().getActivationDateForActiveScript(USERNAME)).isNotEqualTo(ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC));
    }

    @Test
    default void getActivationDateForActiveScriptShouldThrowOnMissingActiveScript() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThatThrownBy(() -> sieveRepository().getActivationDateForActiveScript(USERNAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void haveSpaceShouldNotThrowWhenUserDoesNotHaveQuota() throws Exception {
        sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong() + 1L);
    }

    @Test
    default void haveSpaceShouldNotThrowWhenQuotaIsNotReached() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong());
    }

    @Test
    default void haveSpaceShouldThrowWhenQuotaIsExceed() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        assertThatThrownBy(() -> sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong() + 1))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    default void haveSpaceShouldNotThrowWhenAttemptToReplaceOtherScript() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong());
    }

    @Test
    default void haveSpaceShouldThrowWhenAttemptToReplaceOtherScriptWithTooLargeScript() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThatThrownBy(() -> sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong() + 1))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    default void haveSpaceShouldTakeAlreadyExistingScriptsIntoAccount() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThatThrownBy(() -> sieveRepository().haveSpace(USERNAME, OTHER_SCRIPT_NAME, USER_QUOTA.asLong() - 1))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    default void haveSpaceShouldNotThrowAfterActivatingAScript() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong());
    }

    @Test
    default void listScriptsShouldReturnAnEmptyListIfUserNotFound() throws Exception {
        assertThat(sieveRepository().listScripts(USERNAME)).isEmpty();
    }

    @Test
    default void listScriptsShouldReturnEmptyListWhenThereIsNoScript() throws Exception {
        assertThat(sieveRepository().listScripts(USERNAME)).isEmpty();
    }

    @Test
    default void putScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(sieveRepository().listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, false, SCRIPT_CONTENT.length()));
    }

    @Test
    default void setActiveShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        assertThat(sieveRepository().listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, true, SCRIPT_CONTENT.length()));
    }

    @Test
    default void listScriptShouldCombineActiveAndPassiveScripts() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        assertThat(sieveRepository().listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, true, SCRIPT_CONTENT.length()),
            new ScriptSummary(OTHER_SCRIPT_NAME, false, OTHER_SCRIPT_CONTENT.length()));
    }

    @Test
    default void putScriptShouldThrowWhenScriptTooBig() throws Exception {
        sieveRepository().setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length() - 1));
        assertThatThrownBy(() -> sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    default void putScriptShouldThrowWhenQuotaChangedInBetween() throws Exception {
        sieveRepository().setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length()));
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length() - 1));
        assertThatThrownBy(() -> sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT))
            .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    default void setActiveScriptShouldThrowOnNonExistentScript() {
        assertThatThrownBy(() -> sieveRepository().setActive(USERNAME, SCRIPT_NAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void setActiveScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository().getActive(USERNAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    default void setActiveSwitchScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository().getActive(USERNAME))).isEqualTo(OTHER_SCRIPT_CONTENT);
    }

    @Test
    default void switchOffActiveScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().setActive(USERNAME, SieveRepository.NO_SCRIPT_NAME);
        assertThatThrownBy(() -> sieveRepository().getActive(USERNAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void switchOffActiveScriptShouldNotThrow() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().setActive(USERNAME, SieveRepository.NO_SCRIPT_NAME);
    }

    @Test
    default void getActiveShouldThrowWhenNoActiveScript() {
        assertThatThrownBy(() -> sieveRepository().getActive(USERNAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void deleteActiveScriptShouldThrowIfScriptDoNotExist() {
        assertThatThrownBy(() -> sieveRepository().deleteScript(USERNAME, SCRIPT_NAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void deleteActiveScriptShouldThrow() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        assertThatThrownBy(() -> sieveRepository().deleteScript(USERNAME, SCRIPT_NAME))
            .isInstanceOf(IsActiveException.class);
    }

    @Test
    default void deleteScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().deleteScript(USERNAME, SCRIPT_NAME);
        assertThatThrownBy(() -> sieveRepository().getScript(USERNAME, SCRIPT_NAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void renameScriptShouldThrowIfScriptNotFound() {
        assertThatThrownBy(() -> sieveRepository().renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME))
            .isInstanceOf(ScriptNotFoundException.class);
    }

    @Test
    default void renameScriptShouldWork() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository().getScript(USERNAME, OTHER_SCRIPT_NAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    default void renameScriptShouldPropagateActiveScript() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().setActive(USERNAME, SCRIPT_NAME);
        sieveRepository().renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository().getActive(USERNAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    default void renameScriptShouldNotOverwriteExistingScript() throws Exception {
        sieveRepository().putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository().putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        assertThatThrownBy(() -> sieveRepository().renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME))
            .isInstanceOf(DuplicateException.class);
    }

    @Test
    default void getQuotaShouldThrowIfQuotaNotFound() {
        assertThatThrownBy(() -> sieveRepository().getDefaultQuota())
            .isInstanceOf(QuotaNotFoundException.class);
    }

    @Test
    default void getQuotaShouldWork() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository().getDefaultQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    default void getQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        assertThat(sieveRepository().getQuota(USERNAME)).isEqualTo(USER_QUOTA);
    }

    @Test
    default void hasQuotaShouldReturnFalseWhenRepositoryDoesNotHaveQuota() throws Exception {
        assertThat(sieveRepository().hasDefaultQuota()).isFalse();
    }

    @Test
    default void hasQuotaShouldReturnTrueWhenRepositoryHaveQuota() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository().hasDefaultQuota()).isTrue();
    }

    @Test
    default void hasQuotaShouldReturnFalseWhenUserDoesNotHaveQuota() throws Exception {
        assertThat(sieveRepository().hasDefaultQuota()).isFalse();
    }

    @Test
    default void hasQuotaShouldReturnTrueWhenUserHaveQuota() throws Exception {
        sieveRepository().setQuota(USERNAME, DEFAULT_QUOTA);
        assertThat(sieveRepository().hasQuota(USERNAME)).isTrue();
    }

    @Test
    default void removeQuotaShouldNotThrowIfRepositoryDoesNotHaveQuota() throws Exception {
        sieveRepository().removeQuota();
    }

    @Test
    default void removeUserQuotaShouldNotThrowWhenAbsent() throws Exception {
        sieveRepository().removeQuota(USERNAME);
    }

    @Test
    default void removeQuotaShouldWorkOnRepositories() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository().removeQuota();
        assertThat(sieveRepository().hasDefaultQuota()).isFalse();
    }

    @Test
    default void removeQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().removeQuota(USERNAME);
        assertThat(sieveRepository().hasQuota(USERNAME)).isFalse();
    }

    @Test
    default void removeQuotaShouldWorkOnUsersWithGlobalQuota() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().removeQuota(USERNAME);
        assertThatThrownBy(() -> sieveRepository().getQuota(USERNAME))
            .isInstanceOf(QuotaNotFoundException.class);
    }

    @Test
    default void setQuotaShouldWork() throws Exception {
        sieveRepository().setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository().getDefaultQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    default void setQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository().setQuota(USERNAME, DEFAULT_QUOTA);
        assertThat(sieveRepository().getQuota(USERNAME)).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    default void setQuotaShouldOverrideExistingQuota() throws Exception {
        sieveRepository().setQuota(USERNAME, USER_QUOTA);
        sieveRepository().setQuota(USERNAME, QuotaSizeLimit.size(USER_QUOTA.asLong() - 1));
        assertThat(sieveRepository().getQuota(USERNAME)).isEqualTo(QuotaSizeLimit.size(USER_QUOTA.asLong() - 1));
    }

    default ScriptContent getScriptContent(InputStream inputStream) throws IOException {
        return new ScriptContent(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
    }
}
