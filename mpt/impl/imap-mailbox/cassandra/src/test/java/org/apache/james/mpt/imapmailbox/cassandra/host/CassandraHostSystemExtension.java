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
package org.apache.james.mpt.imapmailbox.cassandra.host;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class CassandraHostSystemExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private final CassandraClusterExtension cassandraExtension;
    private CassandraHostSystem system;

    public CassandraHostSystemExtension() {
        this.cassandraExtension = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        cassandraExtension.afterAll(extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        system.afterTest();
        cassandraExtension.afterEach(extensionContext);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        cassandraExtension.beforeAll(extensionContext);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        cassandraExtension.beforeEach(extensionContext);
        system = new CassandraHostSystem(cassandraExtension.getCassandraCluster());
        system.beforeTest();
    }

    public JamesImapHostSystem getImapHostSystem() {
        return system;
    }
}
