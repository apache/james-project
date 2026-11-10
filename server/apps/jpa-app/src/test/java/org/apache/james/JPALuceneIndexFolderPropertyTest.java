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

package org.apache.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.apache.james.JPALuceneIndexFolderTest.LuceneDirectoryProbe;
import org.apache.james.modules.mailbox.LuceneSearchMailboxModule;
import org.apache.james.utils.GuiceProbe;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

class JPALuceneIndexFolderPropertyTest {
    static {
        System.setProperty(LuceneSearchMailboxModule.LUCENE_INDEX_FOLDER_PROPERTY, "custom/lucene");
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<JPAJamesConfiguration>(tmpDir ->
        JPAJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> JPAJamesServerMain.createServer(configuration)
            .overrideWith(new TestJPAConfigurationModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(LuceneDirectoryProbe.class)))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @AfterAll
    static void clearProperty() {
        System.clearProperty(LuceneSearchMailboxModule.LUCENE_INDEX_FOLDER_PROPERTY);
    }

    @Test
    void luceneIndexShouldBeStoredInTheConfiguredFolderOfTheWorkingDirectory(GuiceJamesServer server) throws Exception {
        LuceneDirectoryProbe probe = server.getProbe(LuceneDirectoryProbe.class);
        Path workingDirectory = probe.fileSystem.getBasedir().toPath().toRealPath();

        assertThat(probe.directory)
            .isInstanceOfSatisfying(FSDirectory.class, directory ->
                assertThat(directory.getDirectory()).isEqualTo(workingDirectory.resolve("custom/lucene")));
    }
}
