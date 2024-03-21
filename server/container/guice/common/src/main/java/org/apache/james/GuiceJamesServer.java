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
import java.util.Collection;
import java.util.List;

import jakarta.annotation.PreDestroy;

import org.apache.james.modules.CommonServicesModule;
import org.apache.james.modules.IsStartedProbeModule;
import org.apache.james.onami.lifecycle.Stager;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.GuiceProbeProvider;
import org.apache.james.utils.InitializationOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

public class GuiceJamesServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceJamesServer.class);
    private static final boolean SHOULD_EXIT_ON_STARTUP_ERROR = Boolean.valueOf(System.getProperty("james.exit.on.startup.error", "true"));

    protected final Module module;
    private final IsStartedProbe isStartedProbe;
    private Stager<PreDestroy> preDestroy;
    private GuiceProbeProvider guiceProbeProvider;

    public static GuiceJamesServer forConfiguration(Configuration configuration) {
        IsStartedProbe isStartedProbe = new IsStartedProbe();

        return new GuiceJamesServer(
            isStartedProbe,
            Modules.combine(
                new IsStartedProbeModule(isStartedProbe),
                new CommonServicesModule(configuration)));
    }

    protected GuiceJamesServer(IsStartedProbe isStartedProbe, Module module) {
        this.isStartedProbe = isStartedProbe;
        this.module = module;
    }
    
    public GuiceJamesServer combineWith(Module... modules) {
        return combineWith(Arrays.asList(modules));
    }

    public GuiceJamesServer combineWith(Collection<Module> modules) {
        return new GuiceJamesServer(isStartedProbe, Modules.combine(Iterables.concat(Arrays.asList(module), modules)));
    }

    public GuiceJamesServer overrideWith(Module... overrides) {
        return new GuiceJamesServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public GuiceJamesServer overrideWith(List<Module> overrides) {
        return new GuiceJamesServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public void start() throws Exception {
        try {
            Injector injector = Guice.createInjector(module);
            guiceProbeProvider = injector.getInstance(GuiceProbeProvider.class);
            preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {
            }));
            injector.getInstance(ConfigurationSanitizingPerformer.class).sanitize();
            injector.getInstance(StartUpChecksPerformer.class).performCheck();
            injector.getInstance(InitializationOperations.class).initModules();
            isStartedProbe.notifyStarted();
            LOGGER.info("JAMES server started");
        } catch (Throwable e) {
            LOGGER.error("Fatal error while starting James", e);
            if (SHOULD_EXIT_ON_STARTUP_ERROR) {
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    public void stop() {
        isStartedProbe.notifyStoped();
        if (preDestroy != null) {
            preDestroy.stage();
        }
        LOGGER.info("JAMES server stopped");
    }

    public boolean isStarted() {
        return isStartedProbe.isStarted();
    }

    public <T extends GuiceProbe> T getProbe(Class<T> probe) {
        return guiceProbeProvider.getProbe(probe);
    }
}
