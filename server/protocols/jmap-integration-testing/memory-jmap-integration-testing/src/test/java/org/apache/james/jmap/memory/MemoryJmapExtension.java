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
package org.apache.james.jmap.memory;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.methods.integration.JamesWithSpamAssassin;
import org.apache.james.jmap.methods.integration.SpamAssassinModule;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.util.scanner.SpamAssassinExtension;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

public class MemoryJmapExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final int LIMIT_TO_20_MESSAGES = 20;

    private final TemporaryFolder temporaryFolder;
    private final SpamAssassinExtension spamAssassinExtension;
    private final JamesWithSpamAssassin james;

    public MemoryJmapExtension() {
        this.temporaryFolder = new TemporaryFolder();
        this.spamAssassinExtension = new SpamAssassinExtension();
        this.james = james();
    }

    private JamesWithSpamAssassin james() {
        return new JamesWithSpamAssassin(
            new GuiceJamesServer()
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestFilesystemModule(temporaryFolder),
                    new TestJMAPServerModule(LIMIT_TO_20_MESSAGES))
                .overrideWith(binder -> binder.bind(PersistenceAdapter.class).to(MemoryPersistenceAdapter.class))
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(binder -> binder.bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class))
                .overrideWith(new SpamAssassinModule(spamAssassinExtension)),
            spamAssassinExtension);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        spamAssassinExtension.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        spamAssassinExtension.afterAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        temporaryFolder.create();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        temporaryFolder.delete();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == JamesWithSpamAssassin.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return james;
    }
}
