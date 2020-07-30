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

package org.apache.james.jmap.rfc8621;


import org.apache.james.jmap.JMAPRoutesHandler;
import org.apache.james.jmap.Version;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.jmap.http.BasicAuthenticationStrategy;
import org.apache.james.jmap.http.SessionRoutes;
import org.apache.james.jmap.http.rfc8621.InjectionKeys;
import org.apache.james.jmap.json.Serializer;
import org.apache.james.jmap.jwt.JWTAuthenticationStrategy;
import org.apache.james.jmap.method.CoreEchoMethod;
import org.apache.james.jmap.method.MailboxGetMethod;
import org.apache.james.jmap.method.Method;
import org.apache.james.jmap.routes.JMAPApiRoutes;
import org.apache.james.metrics.api.MetricFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class RFC8621MethodsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Serializer.class).in(Scopes.SINGLETON);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(CoreEchoMethod.class);
        methods.addBinding().to(MailboxGetMethod.class);
    }

    @ProvidesIntoSet
    JMAPRoutesHandler routesHandler(SessionRoutes sessionRoutes, JMAPApiRoutes jmapApiRoutes) {
        return new JMAPRoutesHandler(Version.RFC8621, jmapApiRoutes, sessionRoutes);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.RFC_8621)
    Authenticator provideAuthenticator(MetricFactory metricFactory,
                                       BasicAuthenticationStrategy basicAuthenticationStrategy,
                                       JWTAuthenticationStrategy jwtAuthenticationStrategy) {
        return Authenticator.of(
            metricFactory,
            basicAuthenticationStrategy,
            jwtAuthenticationStrategy);
    }
}
