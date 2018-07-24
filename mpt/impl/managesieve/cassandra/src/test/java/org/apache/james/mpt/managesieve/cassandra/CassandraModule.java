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

package org.apache.james.mpt.managesieve.cassandra;

import org.apache.james.mpt.host.ManageSieveHostSystem;
import org.apache.james.mpt.managesieve.cassandra.host.CassandraHostSystem;
import org.apache.james.util.Host;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class CassandraModule extends AbstractModule {

    private final Host cassandraHost;

    public CassandraModule(Host cassandraHost) {
        this.cassandraHost = cassandraHost;
    }
    
    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public ManageSieveHostSystem provideHostSystem() throws Exception {
        return new CassandraHostSystem(cassandraHost);
    }
}
