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

import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.ElasticSearchMailboxModule;
import org.apache.james.modules.server.ActiveMQQueueModule;
import org.apache.james.modules.server.ConfigurationPerformerModule;
import org.apache.james.modules.server.DNSServiceModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraJamesServerMain {

    public static final Module defaultModule = Modules.combine(
            new CassandraMailboxModule(),
            new ConfigurationPerformerModule(),
            new CassandraSessionModule(),
            new ElasticSearchMailboxModule(),
            new CassandraUsersRepositoryModule(),
            new CassandraDomainListModule(),
            new CassandraRecipientRewriteTableModule(),
            new DNSServiceModule(),
            new ActiveMQQueueModule());

    public static void main(String[] args) throws Exception {
        CassandraJamesServer server = new CassandraJamesServer(defaultModule);
        server.start();
    }

}
