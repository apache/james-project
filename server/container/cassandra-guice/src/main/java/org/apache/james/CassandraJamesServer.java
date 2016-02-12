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

import javax.annotation.PreDestroy;

import org.apache.james.jmap.JMAPServer;
import org.apache.james.utils.ConfigurationsPerformer;
import org.apache.james.utils.ExtendedServerProbe;
import org.apache.james.utils.GuiceServerProbe;
import org.apache.onami.lifecycle.core.Stager;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

public class CassandraJamesServer {

    private final Module serverModule;
    private Stager<PreDestroy> preDestroy;
    private GuiceServerProbe serverProbe;
    private int jmapPort;

    public CassandraJamesServer(Module serverModule) {
        this.serverModule = serverModule;
    }

    public void start() throws Exception {
        Injector injector = Guice.createInjector(serverModule);
        injector.getInstance(ConfigurationsPerformer.class).initModules();
        preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {}));
        serverProbe = injector.getInstance(GuiceServerProbe.class);
        jmapPort = injector.getInstance(JMAPServer.class).getPort();
    }

    public void stop() {
        preDestroy.stage();
    }

    public ExtendedServerProbe serverProbe() {
        return serverProbe;
    }

    public int getJmapPort() {
        return jmapPort;
    }
}
