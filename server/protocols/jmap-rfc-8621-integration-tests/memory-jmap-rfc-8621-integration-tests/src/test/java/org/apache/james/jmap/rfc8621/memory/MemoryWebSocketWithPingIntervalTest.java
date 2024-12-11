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

package org.apache.james.jmap.rfc8621.memory;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.File;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.rfc8621.contract.IdentityProbeModule;
import org.apache.james.jmap.rfc8621.contract.WebSocketWithPingIntervalContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;

import scala.collection.immutable.Map;
import scala.jdk.javaapi.CollectionConverters;

public class MemoryWebSocketWithPingIntervalTest implements WebSocketWithPingIntervalContract {
    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(Map<String, Object> overrideJmapProperties) {
        guiceJamesServer = MemoryJamesServerMain.createServer(MemoryJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .enableJMAP()
                .build())
            .overrideWith(new TestJMAPServerModule(CollectionConverters.asJava(overrideJmapProperties)), new DelegationProbeModule(), new IdentityProbeModule());
        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }
}
