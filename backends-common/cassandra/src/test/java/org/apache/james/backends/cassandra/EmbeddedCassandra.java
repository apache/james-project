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
package org.apache.james.backends.cassandra;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

import com.google.common.base.Throwables;

public class EmbeddedCassandra {

    private int port;

    public static EmbeddedCassandra createStartServer() {
        return new EmbeddedCassandra();
    }

    private EmbeddedCassandra() {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, TimeUnit.SECONDS.toMillis(20));
            port = EmbeddedCassandraServerHelper.getNativeTransportPort();
        } catch (ConfigurationException | TTransportException | IOException e) {
            Throwables.propagate(e);
        }
    }

    public int getPort() {
        return port;
    }
}
