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
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.onami.lifecycle.Stager;
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
    private Stager<PreDestroy> preDestroy;
    private GuiceProbeProvider guiceProbeProvider;
    private boolean isStarted = false;

    public GuiceJamesServer() {
        this(Modules.combine(
                        new CommonServicesModule(),
                        new MailetProcessingModule()));
    }

    protected GuiceJamesServer(Module module) {
        this.module = module;
    }
    
    public GuiceJamesServer combineWith(Module... modules) {
        return new GuiceJamesServer(Modules.combine(Iterables.concat(Arrays.asList(module), Arrays.asList(modules))));
    }

    public GuiceJamesServer overrideWith(Module... overrides) {
        return new GuiceJamesServer(Modules.override(module).with(overrides));
    }

    public GuiceJamesServer overrideWith(List<Module> overrides) {
        return new GuiceJamesServer(Modules.override(module).with(overrides));
    }

    public void start() throws Exception {
        Injector injector = Guice.createInjector(module);
        preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {}));
        injector.getInstance(ConfigurationsPerformer.class).initModules();
        guiceProbeProvider = injector.getInstance(GuiceProbeProvider.class);
        isStarted = true;
    }

    public void stop() {
        if (preDestroy != null) {
            preDestroy.stage();
            isStarted = false;
        }
    }

    public boolean isStarted() {
        return isStarted;
    }

    public <T extends GuiceProbe> T getProbe(Class<T> probe) {
        return guiceProbeProvider.getProbe(probe);
    }
}
