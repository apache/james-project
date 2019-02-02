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

package org.apache.james.jmap.memory.cucumber;

import javax.inject.Inject;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.methods.integration.cucumber.ImapStepdefs;
import org.apache.james.jmap.methods.integration.cucumber.MainStepdefs;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.rules.TemporaryFolder;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class MemoryStepdefs {

    private static final long LIMIT_TO_3_MESSAGES = 3;
    private final MainStepdefs mainStepdefs;
    private final ImapStepdefs imapStepdefs;
    private final TemporaryFolder temporaryFolder;

    @Inject
    private MemoryStepdefs(MainStepdefs mainStepdefs, ImapStepdefs imapStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.imapStepdefs = imapStepdefs;
        this.temporaryFolder = new TemporaryFolder();
    }

    @Before
    public void init() throws Exception {
        temporaryFolder.create();
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        mainStepdefs.messageIdFactory = new InMemoryMessageId.Factory();
        mainStepdefs.jmapServer = GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_3_MESSAGES),
                        (binder) -> binder.bind(MessageId.Factory.class).toInstance(mainStepdefs.messageIdFactory))
                .overrideWith((binder) -> binder.bind(PersistenceAdapter.class).to(MemoryPersistenceAdapter.class));
        mainStepdefs.init();
    }

    @After
    public void tearDown() {
        imapStepdefs.closeConnections();
        mainStepdefs.tearDown();
        temporaryFolder.delete();
    }
}
