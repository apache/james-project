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

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.PreDestroy;

import org.apache.james.jmap.JMAPServer;
import org.apache.james.modules.CommonServicesModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.ProtocolsModule;
import org.apache.james.utils.ConfigurationsPerformer;
import org.apache.james.utils.ExtendedServerProbe;
import org.apache.james.utils.GuiceServerProbe;
import org.apache.james.webadmin.Port;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.onami.lifecycle.core.Stager;

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

public class GuiceJamesServer {

    private final Module module;
    private Stager<PreDestroy> preDestroy;
    private GuiceServerProbe serverProbe;
    private int jmapPort;
    private Optional<Port> webadminPort;

    public GuiceJamesServer() {
        this(Modules.combine(
                        new CommonServicesModule(),
                        new ProtocolsModule(),
                        new MailetProcessingModule()));
    }

    private GuiceJamesServer(Module module) {
        this.module = module;
    }
    
    public GuiceJamesServer combineWith(Module... modules) {
        return new GuiceJamesServer(Modules.combine(Iterables.concat(Arrays.asList(module), Arrays.asList(modules))));
    }
    
    public GuiceJamesServer overrideWith(Module... overrides) {
        return new GuiceJamesServer(Modules.override(module).with(overrides));
    }
    
    public void start() throws Exception {
        Injector injector = Guice.createInjector(module);
        preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {}));
        injector.getInstance(ConfigurationsPerformer.class).initModules();
        serverProbe = injector.getInstance(GuiceServerProbe.class);
        jmapPort = injector.getInstance(JMAPServer.class).getPort();
        webadminPort =locateWebAdminPort(injector);
    }

    private Optional<Port> locateWebAdminPort(Injector injector) {
        try {
            return Optional.of(injector.getInstance(WebAdminServer.class).getPort());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    public void stop() {
        if (preDestroy != null) {
            preDestroy.stage();
        }
    }

    public ExtendedServerProbe serverProbe() {
        return serverProbe;
    }

    public int getJmapPort() {
        return jmapPort;
    }

    public Optional<Port> getWebadminPort() {
        return webadminPort;
    }
}
