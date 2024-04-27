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

package org.apache.james.cli;

import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.GuiceJamesServer;
import org.apache.james.TemporaryJamesServer;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.data.MemoryUsersRepositoryModule;
import org.apache.james.modules.server.JMXServerModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

class JmxSecurityServerIntegrationTest {

    private static final List<String> BASE_CONFIGURATION_FILE_NAMES = ImmutableList.of("dnsservice.xml",
        "dnsservice.xml",
        "imapserver.xml",
        "jwt_publickey",
        "lmtpserver.xml",
        "mailetcontainer.xml",
        "mailrepositorystore.xml",
        "managesieveserver.xml",
        "pop3server.xml",
        "smtpserver.xml");

    private GuiceJamesServer jamesServer;

    @BeforeEach
    void beforeEach(@TempDir Path workingPath) throws Exception {
        TemporaryJamesServer temporaryJamesServer = new TemporaryJamesServer(workingPath.toFile(), BASE_CONFIGURATION_FILE_NAMES);
        writeFile(workingPath + "/conf/jmx.properties", "jmx.address=127.0.0.1\n" +
            "jmx.port=9999\n");
        writeFile(workingPath + "/conf/jmxremote.password", "james-admin pass1\n");
        writeFile(workingPath + "/conf/jmxremote.access", "james-admin readwrite\n");

        jamesServer = temporaryJamesServer.getJamesServer()
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .combineWith(new UsersRepositoryModuleChooser(new MemoryUsersRepositoryModule())
                .chooseModules(UsersRepositoryModuleChooser.Implementation.DEFAULT))
            .overrideWith(new JMXServerModule(),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)));
        jamesServer.start();

    }

    @AfterEach
    void afterEach() {
        if (jamesServer != null && jamesServer.isStarted()) {
            jamesServer.stop();
        }
    }

    @Test
    void jamesCliShouldFailWhenNotGiveAuthCredential() throws Exception {
        OutputCapture outputCapture = new OutputCapture();

        assertThatThrownBy(() -> ServerCmd.executeAndOutputToStream(new String[]{"-h", "127.0.0.1", "-p", "9999", "listdomains"}, outputCapture.getPrintStream()))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Authentication failed! Credentials required");
    }

    @Test
    void jamesCliShouldWorkWhenGiveAuthCredential() throws Exception {
        OutputCapture outputCapture = new OutputCapture();
        ServerCmd.executeAndOutputToStream(new String[]{"-h", "127.0.0.1", "-p", "9999", "-username", "james-admin", "-password", "pass1",
            "listdomains"}, outputCapture.getPrintStream());

        assertThat(outputCapture.getContent()).contains("localhost");
    }

    @SuppressWarnings("checkstyle:emptycatchblock")
    private void writeFile(String fileNamePath, String data) {
        File passwordFile = new File(fileNamePath);
        try (OutputStream outputStream = new FileOutputStream(passwordFile)) {
            IOUtils.write(data, outputStream, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
