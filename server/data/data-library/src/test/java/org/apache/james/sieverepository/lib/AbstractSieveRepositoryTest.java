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

import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractSieveRepositoryTest {

    private static final String USER = "test";
    private static final String SCRIPT_NAME = "script";
    private static final String SCRIPT_CONTENT = "01234567";
    private static final String OTHER_SCRIPT_NAME = "other_script";
    private static final String OTHER_SCRIPT_CONTENT = "abcdef";
    private static final long DEFAULT_QUOTA = Long.MAX_VALUE - 1L;
    private static final long USER_QUOTA = Long.MAX_VALUE / 2;

    private SieveRepository sieveRepository;

    @Before
    public void setUp() throws Exception {
        sieveRepository = createSieveRepository();
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
    }

    @Test(expected = UserNotFoundException.class)
    public void getScriptShouldThrowIfUserDoesNotExist() throws Exception {
        sieveRepository.getScript(USER, SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void getScriptShouldThrowIfUnableToFindScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.getScript(USER, SCRIPT_NAME);
    }

    @Test
    public void getScriptShouldReturnCorrectContent() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(sieveRepository.getScript(USER, SCRIPT_NAME)).isEqualTo(SCRIPT_CONTENT);
    }

    @Test(expected = UserNotFoundException.class)
    public void haveSpaceShouldThrowIfUserDoesNotExist() throws Exception {
        sieveRepository.haveSpace(USER, SCRIPT_NAME, DEFAULT_QUOTA + 1L);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenUserDoesNotHaveQuota() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, DEFAULT_QUOTA + 1L);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenQuotaIsNotReached() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(DEFAULT_QUOTA);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, DEFAULT_QUOTA);
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldThrowWhenQuotaIsExceed() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(DEFAULT_QUOTA);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, DEFAULT_QUOTA + 1);
    }

    @Test
    public void haveSpaceShouldNotThrowWhenAttemptToReplaceOtherScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, USER_QUOTA);
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldThrowWhenAttemptToReplaceOtherScriptWithTooLargeScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, USER_QUOTA + 1);
    }

    @Test(expected = QuotaExceededException.class)
    public void haveSpaceShouldTakeAlreadyExistingScriptsIntoAccount() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.haveSpace(USER, OTHER_SCRIPT_NAME, USER_QUOTA - 1);
    }

    @Test
    public void haveSpaceShouldNotThrowAfterActivatingAScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.haveSpace(USER, SCRIPT_NAME, USER_QUOTA);
    }

    @Test(expected = UserNotFoundException.class)
    public void listScriptsShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.listScripts(USER);
    }

    @Test
    public void listScriptsShouldReturnEmptyListWhenThereIsNoScript() throws Exception {
        sieveRepository.addUser(USER);
        assertThat(sieveRepository.listScripts(USER)).isEmpty();
    }

    @Test
    public void putScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        assertThat(sieveRepository.listScripts(USER)).containsOnly(new ScriptSummary(SCRIPT_NAME, false));
    }

    @Test
    public void setActiveShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        assertThat(sieveRepository.listScripts(USER)).containsOnly(new ScriptSummary(SCRIPT_NAME, true));
    }

    @Test
    public void listScriptShouldCombineActiveAndPassiveScripts() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.putScript(USER, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        assertThat(sieveRepository.listScripts(USER)).containsOnly(new ScriptSummary(SCRIPT_NAME, true), new ScriptSummary(OTHER_SCRIPT_NAME, false));
    }

    @Test(expected = UserNotFoundException.class)
    public void putScriptShouldThrowIfUserDoesNotExist() throws Exception {
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
    }

    @Test(expected = QuotaExceededException.class)
    public void putScriptShouldThrowWhenScriptTooBig() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(SCRIPT_CONTENT.length() - 1);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
    }

    @Test(expected = QuotaExceededException.class)
    public void putScriptShouldThrowWhenQuotaChangedInBetween() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(SCRIPT_CONTENT.length());
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setQuota(SCRIPT_CONTENT.length() - 1);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
    }

    @Test
    public void hasUserShouldReturnFalseOnNonExistingUser() throws Exception {
        assertThat(sieveRepository.hasUser(USER)).isFalse();
    }

    @Test
    public void hasUserShouldReturnTrueOnExistingUser() throws Exception {
        sieveRepository.addUser(USER);
        assertThat(sieveRepository.hasUser(USER)).isTrue();
    }

    @Test(expected = UserNotFoundException.class)
    public void removeUserShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.removeUser(USER);
    }

    @Test
    public void removeUserShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.removeUser(USER);
        assertThat(sieveRepository.hasUser(USER)).isFalse();
    }

    @Test(expected = UserNotFoundException.class)
    public void setActiveScriptShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.setActive(USER, SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void setActiveScriptShouldThrowOnNonExistentScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setActive(USER, SCRIPT_NAME);
    }

    @Test
    public void setActiveScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        assertThat(sieveRepository.getActive(USER)).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    public void setActiveSwitchScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.putScript(USER, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveRepository.setActive(USER, OTHER_SCRIPT_NAME);
        assertThat(sieveRepository.getActive(USER)).isEqualTo(OTHER_SCRIPT_CONTENT);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void switchOffActiveScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.setActive(USER, "");
        sieveRepository.getActive(USER);
    }

    @Test
    public void switchOffActiveScriptShouldNotThrow() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.setActive(USER, "");
    }

    @Test(expected = ScriptNotFoundException.class)
    public void getActiveShouldThrowWhenNoActiveScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.getActive(USER);
    }

    @Test(expected = UserNotFoundException.class)
    public void deleteActiveScriptShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.deleteScript(USER, SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void deleteActiveScriptShouldThrowIfScriptDoNotExist() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.deleteScript(USER, SCRIPT_NAME);
    }

    @Test(expected = IsActiveException.class)
    public void deleteActiveScriptShouldThrow() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.deleteScript(USER, SCRIPT_NAME);
    }

    @Test(expected = IsActiveException.class)
    public void deleteScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.deleteScript(USER, SCRIPT_NAME);
        sieveRepository.getScript(USER, SCRIPT_NAME);
    }

    @Test(expected = UserNotFoundException.class)
    public void renameScriptShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.renameScript(USER, SCRIPT_NAME, OTHER_SCRIPT_NAME);
    }

    @Test(expected = ScriptNotFoundException.class)
    public void renameScriptShouldThrowIfScriptNotFound() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.renameScript(USER, SCRIPT_NAME, OTHER_SCRIPT_NAME);
    }

    @Test
    public void renameScriptShouldWork() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.renameScript(USER, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(sieveRepository.getScript(USER, OTHER_SCRIPT_NAME)).isEqualTo(SCRIPT_CONTENT);
    }

    @Test
    public void renameScriptShouldPropagateActiveScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.setActive(USER, SCRIPT_NAME);
        sieveRepository.renameScript(USER, SCRIPT_NAME, OTHER_SCRIPT_NAME);
        assertThat(sieveRepository.getActive(USER)).isEqualTo(SCRIPT_CONTENT);
    }

    @Test(expected = DuplicateException.class)
    public void renameScriptShouldNotOverwriteExistingScript() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.putScript(USER, SCRIPT_NAME, SCRIPT_CONTENT);
        sieveRepository.putScript(USER, OTHER_SCRIPT_NAME, OTHER_SCRIPT_CONTENT);
        sieveRepository.renameScript(USER, SCRIPT_NAME, OTHER_SCRIPT_NAME);
    }

    @Test(expected = UserNotFoundException.class)
    public void getQuotaShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.getQuota(USER);
    }

    @Test(expected = QuotaNotFoundException.class)
    public void getQuotaShouldThrowIfQuotaNotFound() throws Exception {
        sieveRepository.getQuota();
    }

    @Test
    public void getQuotaShouldWork() throws Exception {
        sieveRepository.setQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.getQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    public void getQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        assertThat(sieveRepository.getQuota(USER)).isEqualTo(USER_QUOTA);
    }

    @Test(expected = UserNotFoundException.class)
    public void hasQuotaShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.hasQuota(USER);
    }

    @Test
    public void hasQuotaShouldReturnFalseWhenRepositoryDoesNotHaveQuota() throws Exception {
        assertThat(sieveRepository.hasQuota()).isFalse();
    }

    @Test
    public void hasQuotaShouldReturnTrueWhenRepositoryHaveQuota() throws Exception {
        sieveRepository.setQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.hasQuota()).isTrue();
    }

    @Test
    public void hasQuotaShouldReturnFalseWhenUserDoesNotHaveQuota() throws Exception {
        sieveRepository.addUser(USER);
        assertThat(sieveRepository.hasQuota()).isFalse();
    }

    @Test
    public void hasQuotaShouldReturnTrueWhenUserHaveQuota() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, DEFAULT_QUOTA);
        assertThat(sieveRepository.hasQuota(USER)).isTrue();
    }

    @Test(expected = UserNotFoundException.class)
    public void removeQuotaShouldThrowIfUserNotFound() throws Exception {
        sieveRepository.removeQuota(USER);
    }

    @Test(expected = QuotaNotFoundException.class)
    public void removeQuotaShouldThrowIfRepositoryDoesNotHaveQuota() throws Exception {
        sieveRepository.removeQuota();
    }

    @Test
    public void removeQuotaShouldWorkOnRepositories() throws Exception {
        sieveRepository.setQuota(DEFAULT_QUOTA);
        sieveRepository.removeQuota();
        assertThat(sieveRepository.hasQuota()).isFalse();
    }

    @Test
    public void removeQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.removeQuota(USER);
        assertThat(sieveRepository.hasQuota(USER)).isFalse();
    }

    @Test(expected = QuotaNotFoundException.class)
    public void removeQuotaShouldWorkOnUsersWithGlobalQuota() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(DEFAULT_QUOTA);
        sieveRepository.setQuota(USER, USER_QUOTA);
        sieveRepository.removeQuota(USER);
        sieveRepository.getQuota(USER);
    }

    @Test
    public void setQuotaShouldWork() throws Exception {
        sieveRepository.setQuota(DEFAULT_QUOTA);
        assertThat(sieveRepository.getQuota()).isEqualTo(DEFAULT_QUOTA);
    }

    @Test
    public void setQuotaShouldWorkOnUsers() throws Exception {
        sieveRepository.addUser(USER);
        sieveRepository.setQuota(USER, DEFAULT_QUOTA);
        assertThat(sieveRepository.getQuota(USER)).isEqualTo(DEFAULT_QUOTA);
    }

    protected abstract SieveRepository createSieveRepository() throws Exception;

    protected abstract void cleanUp() throws Exception;

}
