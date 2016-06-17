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

package org.apache.james.modules.server;

import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.apache.james.webadmin.WebAdminServer.WEBADMIN_ENABLED;
import static org.apache.james.webadmin.WebAdminServer.WEBADMIN_PORT;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.FixedPort;
import org.apache.james.webadmin.Port;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.routes.DomainRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class WebAdminServerModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JsonTransformer.class).in(Scopes.SINGLETON);
        bind(WebAdminServer.class).in(Scopes.SINGLETON);

        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(DomainRoutes.class);
        routesMultibinder.addBinding().to(UserRoutes.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(WebAdminServerModuleConfigurationPerformer.class);
    }

    @Provides
    @Named(WEBADMIN_PORT)
    public Port provideWebAdminPort(PropertiesProvider propertiesProvider) throws Exception {
        try {
            return new FixedPort(propertiesProvider.getConfiguration("webadmin").getInt("port", WebAdminServer.DEFAULT_PORT));
        } catch (FileNotFoundException e) {
            return new FixedPort(WebAdminServer.DEFAULT_PORT);
        }
    }

    @Provides
    @Named(WEBADMIN_ENABLED)
    public boolean provideWebAdminEnabled(PropertiesProvider propertiesProvider) throws Exception {
        try {
            return propertiesProvider.getConfiguration("webadmin").getBoolean("enabled", false);
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    @Singleton
    public static class WebAdminServerModuleConfigurationPerformer implements ConfigurationPerformer {

        private final WebAdminServer webAdminServer;

        @Inject
        public WebAdminServerModuleConfigurationPerformer(WebAdminServer webAdminServer) {
            this.webAdminServer = webAdminServer;
        }

        @Override
        public void initModule() {
            try {
                webAdminServer.configure(NO_CONFIGURATION);
            } catch (ConfigurationException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(WebAdminServer.class);
        }
    }

}
