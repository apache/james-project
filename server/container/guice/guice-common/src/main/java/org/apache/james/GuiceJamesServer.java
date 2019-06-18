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
import java.util.List;

import javax.annotation.PreDestroy;

import org.apache.james.modules.CommonServicesModule;
import org.apache.james.modules.IsStartedProbeModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.onami.lifecycle.Stager;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.ConfigurationsPerformer;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.GuiceProbeProvider;

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

public class GuiceJamesServer {

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
                new CommonServicesModule(configuration),
                new MailetProcessingModule()));
    }

    protected GuiceJamesServer(IsStartedProbe isStartedProbe, Module module) {
        this.isStartedProbe = isStartedProbe;
        this.module = module;
    }
    
    public GuiceJamesServer combineWith(Module... modules) {
        return new GuiceJamesServer(isStartedProbe, Modules.combine(Iterables.concat(Arrays.asList(module), Arrays.asList(modules))));
    }

    public GuiceJamesServer overrideWith(Module... overrides) {
        return new GuiceJamesServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public GuiceJamesServer overrideWith(List<Module> overrides) {
        return new GuiceJamesServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public void start() throws Exception {
        Injector injector = Guice.createInjector(module);
        preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {}));
        injector.getInstance(StartUpChecksPerformer.class)
            .performCheck();
        injector.getInstance(ConfigurationsPerformer.class).initModules();
        guiceProbeProvider = injector.getInstance(GuiceProbeProvider.class);
        isStartedProbe.notifyStarted();
    }

    public void stop() {
        isStartedProbe.notifyStoped();
        if (preDestroy != null) {
            preDestroy.stage();
        }
    }

    public boolean isStarted() {
        return isStartedProbe.isStarted();
    }

    public <T extends GuiceProbe> T getProbe(Class<T> probe) {
        return guiceProbeProvider.getProbe(probe);
    }
}
