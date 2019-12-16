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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class MalformedUrlRoutesTest {
    private static final Username USERNAME = Username.of("username");
    private static final String MALFORMED_MAILBOX_NAME = "inbox%work";

    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;

    private void createServer(MailboxManager mailboxManager) throws Exception {
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);

        MemoryTaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new UserMailboxesRoutes(new UserMailboxesService(mailboxManager, usersRepository), new JsonTransformer(),
                taskManager, new InMemoryId.Factory(), mock(ReIndexer.class)))
            .start();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        createServer(InMemoryIntegrationResources.defaultResources().getMailboxManager());
    }

    @Test
    @Disabled()
    void getMailboxesShouldReturnJsonErrorWhenMalformedUrl() throws Exception {
        String response = executeRawPUT(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + MALFORMED_MAILBOX_NAME);
        assertThat(response).doesNotContain("<h1>Bad Message 400</h1><pre>reason: Bad Request</pre>");
    }

    private String executeRawPUT(String path) throws Exception {
        String hostname = "localhost";
        int port = webAdminServer.getPort().getValue();

        InetAddress addr = InetAddress.getByName(hostname);
        try (Socket socket = new Socket(addr, port);
             BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader rd = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send request
            wr.write("PUT " + path + " HTTP/1.1\r\n");
            wr.write("Host: " + hostname + "\r\n");
            wr.write("Content-Type: " + "application/json" + "\r\n");
            wr.write("Accept: " + "application/json" + "\r\n");
            wr.write("\r\n");
            wr.flush();

            // Get response
            StringBuffer response = new StringBuffer();
            rd.lines().forEach(line -> response.append(line + "\r\n"));

            return response.toString();

        }
    }

}
