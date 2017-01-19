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
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.datastax.driver.core.Session;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;


public class EmbeddedCassandraRule implements GuiceModuleTestRule {

    public static class SessionProvider implements Provider<Session> {

        private final Session session;

        @Inject
        private SessionProvider(CassandraCluster cluster) {
            session = cluster.getConf();
        }

        @Override
        public Session get() {
            return session;
        }
    }

    private EmbeddedCassandra cassandra;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                cassandra = EmbeddedCassandra.createStartServer();
                base.evaluate();
            }
        };
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        return Modules.combine(
                (binder) -> binder.bind(EmbeddedCassandra.class).toInstance(cassandra),
                (binder) -> binder.bind(Session.class).toProvider(SessionProvider.class).in(Scopes.SINGLETON));
    }
}
