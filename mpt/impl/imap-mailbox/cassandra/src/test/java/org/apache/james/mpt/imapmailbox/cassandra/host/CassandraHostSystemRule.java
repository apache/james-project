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

import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.junit.rules.ExternalResource;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Throwables;

public class CassandraHostSystemRule extends ExternalResource {

    private static final String USERNAME = "mpt";

    private final DockerCassandraRule cassandraServer;
    private CassandraHostSystem system;

    public CassandraHostSystemRule(DockerCassandraRule cassandraServer) {
        this.cassandraServer = cassandraServer;
    }

    @Override
    protected void before() throws Throwable {
        system = new CassandraHostSystem(cassandraServer.getIp(), cassandraServer.getBindingPort());
        system.beforeTest();
    }

    @Override
    protected void after() {
        try {
            clean();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    public void clean() throws Exception {
        MailboxManager mailboxManager = system.getMailboxManager();
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.list(systemSession)
            .forEach(Throwing.consumer(
                mailboxPath -> mailboxManager.deleteMailbox(
                        mailboxPath, 
                        mailboxManager.createSystemSession(mailboxPath.getUser()))));
    }

    public JamesImapHostSystem getImapHostSystem() {
        return system;
    }
}
