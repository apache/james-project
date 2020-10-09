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

package org.apache.james.mpt.imapmailbox.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.cassandra.host.CassandraHostSystem;
import org.apache.james.mpt.imapmailbox.cassandra.host.CassandraHostSystemRule;
import org.apache.james.mpt.imapmailbox.suite.Listing;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CassandraListingTest extends Listing {
    public DockerCassandraRule cassandraServer = new DockerCassandraRule().allowRestart();
    public CassandraHostSystemRule cassandraHostSystemRule = new CassandraHostSystemRule(cassandraServer);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(cassandraServer).around(cassandraHostSystemRule);

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return cassandraHostSystemRule.getImapHostSystem();
    }

    @Test
    public void listShouldNotReadCounters() throws Exception {
        CassandraHostSystem cassandraHostSystem = (CassandraHostSystem) this.system;
        StatementRecorder statementRecorder = new StatementRecorder();
        cassandraHostSystem.getCassandra()
            .getConf()
            .recordStatements(statementRecorder);

        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ListOnly");

        assertThat(statementRecorder.listExecutedStatements(StatementRecorder.Selector.preparedStatement(
                "SELECT unseen,count FROM mailboxCounters WHERE mailboxId=:mailboxId;")))
            .isEmpty();
    }
}
