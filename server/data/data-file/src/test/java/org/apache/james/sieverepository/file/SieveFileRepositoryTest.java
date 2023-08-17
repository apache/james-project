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

package org.apache.james.sieverepository.file;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.james.core.Username;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.lib.SieveRepositoryContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SieveFileRepositoryTest implements SieveRepositoryContract {

    static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve";

    FileSystem fileSystem;
    SieveRepository sieveRepository;

    @BeforeEach
    void setUp() throws Exception {
        this.fileSystem = new FileSystem() {
            @Override
            public File getBasedir() {
                return new File(System.getProperty("java.io.tmpdir"));
            }
            
            @Override
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(getFile(url));
            }
            
            @Override
            public File getFile(String fileURL) {
                return new File(getBasedir(), fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
            }
        };
        sieveRepository = new SieveFileRepository(fileSystem);
    }

    @AfterEach
    void tearDown() throws Exception {
        File root = fileSystem.getFile(SIEVE_ROOT);
        // Remove files from the previous test, if any
        if (root.exists()) {
            FileUtils.forceDelete(root);
        }
    }

    @Override
    public SieveRepository sieveRepository() {
        return sieveRepository;
    }

    @Test
    void putScriptShouldThrowOnCraftedUsername() {
        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("../../home/interview1/test"), SCRIPT_NAME, SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void putScriptShouldThrowOnCraftedScriptName() {
        assertThatThrownBy(() ->  sieveRepository().putScript(Username.of("test"),
                new ScriptName("../../../../home/interview1/script"), SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void getScriptShouldNotAllowToReadScriptsOfOtherUsers() throws Exception {
        sieveRepository().putScript(Username.of("other"), new ScriptName("script"), new ScriptContent("PWND!!!"));

        assertThatThrownBy(() ->  sieveRepository().getScript(Username.of("test"),
                new ScriptName("../other/script")))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void getScriptShouldNotAllowToReadScriptsOfOtherUsersWhenPrefix() throws Exception {
        sieveRepository().putScript(Username.of("testa"), new ScriptName("script"), new ScriptContent("PWND!!!"));

        assertThatThrownBy(() ->  sieveRepository().getScript(Username.of("test"),
            new ScriptName("../other/script")))
            .isInstanceOf(StorageException.class);
    }
}
