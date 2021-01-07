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

package org.apache.james.spamassassin.mock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MockSpamdExtension implements AfterEachCallback, BeforeEachCallback {

    private ExecutorService executor = Executors.newSingleThreadExecutor(NamedThreadFactory.withClassName(getClass()));
    private MockSpamd spamd = new MockSpamd();

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        executor.shutdownNow();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        spamd.bind();
        executor.execute(spamd);
    }

    public int getPort() {
        return spamd.getPort();
    }
}
