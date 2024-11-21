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

import org.apache.james.DisconnectorNotifier;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.protocols.webadmin.ProtocolServerRoutes;
import org.apache.james.webadmin.Routes;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class ServerRouteModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), AbstractServerFactory.class);

        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(ProtocolServerRoutes.class);

        Multibinder.newSetBinder(binder(), Disconnector.class);
        Multibinder.newSetBinder(binder(), ConnectionDescriptionSupplier.class);

        bind(Disconnector.class).to(Disconnector.CompositeDisconnector.class);
        bind(Disconnector.CompositeDisconnector.class).in(Scopes.SINGLETON);

        bind(ConnectionDescriptionSupplier.class).to(ConnectionDescriptionSupplier.CompositeConnectionDescriptionSupplier.class);
        bind(ConnectionDescriptionSupplier.CompositeConnectionDescriptionSupplier.class).in(Scopes.SINGLETON);

        bind(DisconnectorNotifier.class).to(DisconnectorNotifier.InVMDisconnectorNotifier.class);
        bind(DisconnectorNotifier.InVMDisconnectorNotifier.class).in(Scopes.SINGLETON);
    }
}
