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

import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
import org.apache.james.modules.TestElasticSearchModule;
import org.elasticsearch.node.Node;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;


public class EmbeddedElasticSearchRule implements GuiceModuleTestRule {

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder, MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX);

    private final RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch);

    @Override
    public Statement apply(Statement base, Description description) {
        return chain.apply(base, description);
    }

    @Override
    public void await() {
        embeddedElasticSearch.awaitForElasticSearch();
    }


    @Override
    public Module getModule() {
        return new TestElasticSearchModule(embeddedElasticSearch);
    }

    public Node getNode() {
        return embeddedElasticSearch.getNode();
    }
}
