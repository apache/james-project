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
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.modules.TestElasticSearchModule;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class EmbeddedElasticSearchExtension implements GuiceModuleTestExtension {
    private final TemporaryFolderExtension folderExtension;
    private EmbeddedElasticSearch embeddedElasticSearch;

    public EmbeddedElasticSearchExtension() {
        this.folderExtension = new TemporaryFolderExtension();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        folderExtension.beforeEach(extensionContext);
        embeddedElasticSearch = new EmbeddedElasticSearch(folderExtension.getTemporaryFolder().getTempDir().toPath());
        embeddedElasticSearch.before();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        embeddedElasticSearch.after();
        folderExtension.afterEach(extensionContext);
    }

    @Override
    public Module getModule() {
        return new TestElasticSearchModule(embeddedElasticSearch);
    }

    @Override
    public void await() {
        embeddedElasticSearch.awaitForElasticSearch();
    }

    public EmbeddedElasticSearch getEmbeddedElasticSearch() {
        return embeddedElasticSearch;
    }
}
