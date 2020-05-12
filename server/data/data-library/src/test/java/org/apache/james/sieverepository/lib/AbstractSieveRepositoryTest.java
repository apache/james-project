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
import org.junit.Test;

public abstract class AbstractSieveRepositoryTest {

    protected static final Username USERNAME = Username.of("test");
    protected static final ScriptName SCRIPT_NAME = new ScriptName("script");
    protected static final ScriptContent SCRIPT_CONTENT = new ScriptContent("Hello World");

    private static final ScriptName OTHER_SCRIPT_NAME = new ScriptName("other_script");
    private static final ScriptContent OTHER_SCRIPT_CONTENT = new ScriptContent("Other script content");
    private static final QuotaSizeLimit DEFAULT_QUOTA = QuotaSizeLimit.size(Long.MAX_VALUE - 1L);
    private static final QuotaSizeLimit USER_QUOTA = QuotaSizeLimit.size(Long.MAX_VALUE / 2);

    protected SieveRepository sieveRepository;

    public void setUp() throws Exception {
        sieveRepository = createSieveRepository();
    }

    @Test(expected = ScriptNotFoundException.class)
    public void getScriptShouldThrowIfUnableToFindScript() throws Exception {
        sieveRepository.getScript(USERNAME, SCRIPT_NAME);
    }

    @Test
    public void getScriptShouldReturnCorrectContent() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(getScriptContent(sieveRepository.getScript(USERNAME, SCRIPT_NAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    public void getActivationDateForActiveScriptShouldReturnNonNullAndNonZeroResult() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        assertThat(sieveRepository.getActivationDateForActiveScript(USERNAME)).isNotNull();
        assertThat(sieveRepository.getActivationDateForActiveScript(USERNAME)).isNotEqualTo(ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC));
    }

    @Test(expected = ScriptNotFoundException.class)
    public void getActivationDateForActiveScriptShouldThrowOnMissingActiveScript() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.getActivationDateForActiveScript(USERNAME);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenUserDoesNotHaveQuota() throws Exception {
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong() + 1L);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenQuotaIsNotReached() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong());
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldThrowWhenQuotaIsExceed() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, DEFAULT_QUOTA.asLong() + 1);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenAttemptToReplaceOtherScript() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong());
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldThrowWhenAttemptToReplaceOtherScriptWithTooLargeScript() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong() + 1);
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldTakeAlreadyExistingScriptsIntoAccount() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USERNAME, OTHER_SCRIPT_NAME, USER_QUOTA.asLong() - 1);
    }

    @Test
    public void haveSpaceShouldNotThrowAfterActivatingAScript() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.haveSpace(USERNAME, SCRIPT_NAME, USER_QUOTA.asLong());
    }

    @Test
    public void listScriptsShouldReturnAnEmptyListIfUserNotFound() throws Exception {
        assertThat(sieveRepository.listScripts(USERNAME)).isEmpty();
    }

    @Test
    public void listScriptsShouldReturnEmptyListWhenThereIsNoScript() throws Exception {
        assertThat(sieveRepository.listScripts(USERNAME)).isEmpty();
    }

    @Test
    public void putScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(sieveRepository.listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, false));
    }

    @Test
    public void setActiveShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        assertThat(sieveRepository.listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, true));
    }

    @Test
    public void listScriptShouldCombineActiveAndPassiveScripts() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        assertThat(sieveRepository.listScripts(USERNAME)).containsOnly(new ScriptSummary(SCRIPT_NAME, true), new ScriptSummary(OTHER_SCRIPT_NAME, false));
    }

    @Test(expected = QuotaExceededException.class)
    public void putScriptShouldThrowWhenScriptTooBig() throws Exception {
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length() - 1));
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
    }

    @Test(expected = QuotaExceededException.class)
    public void putScriptShouldThrowWhenQuotaChangedInBetween() throws Exception {
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length()));
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setDefaultQuota(QuotaSizeLimit.size(SCRIPT_CONTENT.length() - 1));
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void setActiveScriptShouldThrowOnNonExistentScript() throws Exception {
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
    }

    @Test
    public void setActiveScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository.getActive(USERNAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    public void setActiveSwitchScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository.getActive(USERNAME))).isEqualTo(OTHER_SCRIPT_CONTENT);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void switchOffActiveScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.setActive(USERNAME, SieveRepository.NO_SCRIPT_NAME);
        sieveRepository.getActive(USERNAME);
    }

    @Test
    public void switchOffActiveScriptShouldNotThrow() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.setActive(USERNAME, SieveRepository.NO_SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void getActiveShouldThrowWhenNoActiveScript() throws Exception {
        sieveRepository.getActive(USERNAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void deleteActiveScriptShouldThrowIfScriptDoNotExist() throws Exception {
        sieveRepository.deleteScript(USERNAME, SCRIPT_NAME);
    }

    @Test(expected = IsActiveException.class)
    public void deleteActiveScriptShouldThrow() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.deleteScript(USERNAME, SCRIPT_NAME);
    }

    @Test(expected = IsActiveException.class)
    public void deleteScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.deleteScript(USERNAME, SCRIPT_NAME);
        sieveRepository.getScript(USERNAME, SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void renameScriptShouldThrowIfScriptNotFound() throws Exception {
        sieveRepository.renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
    }

    @Test
    public void renameScriptShouldWork() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository.getScript(USERNAME, OTHER_SCRIPT_NAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    public void renameScriptShouldPropagateActiveScript() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USERNAME, SCRIPT_NAME);
        sieveRepository.renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(getScriptContent(sieveRepository.getActive(USERNAME))).isEqualTo(SCRIPT_CONTENT);
    }

    @Test(expected = DuplicateException.class)
    public void renameScriptShouldNotOverwriteExistingScript() throws Exception {
        sieveRepository.putScript(USERNAME, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.putScript(USERNAME, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveRepository.renameScript(USERNAME, SCRIPT_NAME, OTHER_SCRIPT_NAME);
    }

    @Test(expected = QuotaNotFoundException.class)
    public void getQuotaShouldThrowIfQuotaNotFound() throws Exception {
        sieveRepository.getDefaultQuota();
    }

    @Test
    public void getQuotaShouldWork() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.getDefaultQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    public void getQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        assertThat(sieveRepository.getQuota(USERNAME)).isEqualTo(USER_QUOTA);
    }

    @Test
    public void hasQuotaShouldReturnFalseWhenRepositoryDoesNotHaveQuota() throws Exception {
        assertThat(sieveRepository.hasDefaultQuota()).isFalse();
    }

    @Test
    public void hasQuotaShouldReturnTrueWhenRepositoryHaveQuota() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.hasDefaultQuota()).isTrue();
    }

    @Test
    public void hasQuotaShouldReturnFalseWhenUserDoesNotHaveQuota() throws Exception {
        assertThat(sieveRepository.hasDefaultQuota()).isFalse();
    }

    @Test
    public void hasQuotaShouldReturnTrueWhenUserHaveQuota() throws Exception {
        sieveRepository.setQuota(USERNAME, DEFAULT_QUOTA);
        assertThat(sieveRepository.hasQuota(USERNAME)).isTrue();
    }

    @Test
    public void removeQuotaShouldNotThrowIfRepositoryDoesNotHaveQuota() throws Exception {
        sieveRepository.removeQuota();
    }

    @Test
    public void removeUserQuotaShouldNotThrowWhenAbsent() throws Exception {
        sieveRepository.removeQuota(USERNAME);
    }

    @Test
    public void removeQuotaShouldWorkOnRepositories() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository.removeQuota();
        assertThat(sieveRepository.hasDefaultQuota()).isFalse();
    }

    @Test
    public void removeQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.removeQuota(USERNAME);
        assertThat(sieveRepository.hasQuota(USERNAME)).isFalse();
    }

    @Test(expected = QuotaNotFoundException.class)
    public void removeQuotaShouldWorkOnUsersWithGlobalQuota() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.removeQuota(USERNAME);
        sieveRepository.getQuota(USERNAME);
    }

    @Test
    public void setQuotaShouldWork() throws Exception {
        sieveRepository.setDefaultQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.getDefaultQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    public void setQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.setQuota(USERNAME, DEFAULT_QUOTA);
        assertThat(sieveRepository.getQuota(USERNAME)).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    public void setQuotaShouldOverrideExistingQuota() throws Exception {
        sieveRepository.setQuota(USERNAME, USER_QUOTA);
        sieveRepository.setQuota(USERNAME, QuotaSizeLimit.size(USER_QUOTA.asLong() - 1));
        assertThat(sieveRepository.getQuota(USERNAME)).isEqualTo(QuotaSizeLimit.size(USER_QUOTA.asLong() - 1));
    }

    protected ScriptContent getScriptContent(InputStream inputStream) throws IOException {
        return new ScriptContent(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
    }

    protected abstract SieveRepository createSieveRepository() throws Exception;

}
