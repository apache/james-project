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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SieveRepositoryManagementTest {
    @Mock
    SieveRepository sieveRepository;

    SieveRepositoryManagement sieveRepositoryManagement;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        sieveRepositoryManagement = new SieveRepositoryManagement();
        sieveRepositoryManagement.setSieveRepository(sieveRepository);
    }

    @Test
    void importSieveScriptFileToRepositoryShouldStoreContentAndActivateScript() throws Exception {
        String userName = "user@domain";
        String script = "user_script";
        URL sieveResource = ClassLoader.getSystemResource("sieve/my_sieve");

        Username username = Username.of(userName);
        ScriptName scriptName = new ScriptName(script);
        String sieveContent = IOUtils.toString(sieveResource, StandardCharsets.UTF_8);
        ScriptContent scriptContent = new ScriptContent(sieveContent);

        sieveRepositoryManagement.addActiveSieveScriptFromFile(userName, script, sieveResource.getFile());

        verify(sieveRepository, times(1)).putScript(username, scriptName, scriptContent);
        verify(sieveRepository, times(1)).setActive(username, scriptName);
    }

    @Test
    void importSieveScriptFileToRepositoryShouldNotImportFileWithWrongPathToRepistory() throws Exception {
        String userName = "user@domain";
        String script = "user_script";
        URL sieveResource = ClassLoader.getSystemResource("sieve/my_sieve");

        Username username = Username.of(userName);
        ScriptName scriptName = new ScriptName(script);
        String sieveContent = IOUtils.toString(sieveResource, StandardCharsets.UTF_8);
        ScriptContent scriptContent = new ScriptContent(sieveContent);

        sieveRepositoryManagement.addActiveSieveScriptFromFile(userName, script, "wrong_path/" + sieveResource.getFile());
        verify(sieveRepository, times(0)).putScript(username, scriptName, scriptContent);
        verify(sieveRepository, times(0)).setActive(username, scriptName);
    }
}