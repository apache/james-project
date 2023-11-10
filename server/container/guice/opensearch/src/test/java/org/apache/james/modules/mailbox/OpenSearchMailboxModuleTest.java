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

package org.apache.james.modules.mailbox;

import static org.apache.james.filesystem.api.FileSystemFixture.RECURSIVE_CLASSPATH_FILE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.util.Host;
import org.apache.james.utils.ExtendedClassLoader;
import org.apache.james.utils.ExtensionConfiguration;
import org.apache.james.utils.GuiceGenericLoader;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;

class OpenSearchMailboxModuleTest {
    @Test
    void test() {
        GuiceGenericLoader genericLoader = new GuiceGenericLoader(
            Guice.createInjector(),
            new ExtendedClassLoader(RECURSIVE_CLASSPATH_FILE_SYSTEM),
            ExtensionConfiguration.DEFAULT);

        Set<ListeningMessageSearchIndex.SearchOverride> searchOverrides = new OpenSearchMailboxConfigurationModule()
            .provideSearchOverrides(genericLoader,
            OpenSearchConfiguration.builder()
                .addHost(Host.parseConfString("127.0.0.1", 9200))
                .withSearchOverrides(ImmutableList.of(
                    "org.apache.james.modules.mailbox.SearchOverrideA",
                    "org.apache.james.modules.mailbox.SearchOverrideB"))
                .build());

        assertThat(searchOverrides)
            .hasSize(2)
            .<Class>extracting(ListeningMessageSearchIndex.SearchOverride::getClass)
            .containsOnly(SearchOverrideA.class, SearchOverrideB.class);
    }
}