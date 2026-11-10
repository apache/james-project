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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.apache.james.PostgresJamesConfiguration.EventBusImpl;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.lucene.search.LuceneMessageSearchIndex;
import org.apache.james.mailbox.lucene.search.LuceneSearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

class PostgresWithLuceneSearchTest {
    public static class LuceneProbe implements GuiceProbe {
        private final MessageSearchIndex messageSearchIndex;
        private final SearchHighlighter searchHighlighter;

        @Inject
        public LuceneProbe(MessageSearchIndex messageSearchIndex, SearchHighlighter searchHighlighter) {
            this.messageSearchIndex = messageSearchIndex;
            this.searchHighlighter = searchHighlighter;
        }
    }

    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.lucene())
            .usersRepository(DEFAULT)
            .eventBusImpl(EventBusImpl.IN_MEMORY)
            .build())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(LuceneProbe.class)))
        .extension(postgresExtension)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void shouldStart(GuiceJamesServer server) {
        assertThat(server.isStarted()).isTrue();
    }

    @Test
    void messageSearchIndexShouldBeLucene(GuiceJamesServer server) {
        assertThat(server.getProbe(LuceneProbe.class).messageSearchIndex)
            .isInstanceOf(LuceneMessageSearchIndex.class);
    }

    @Test
    void searchHighlighterShouldBeLucene(GuiceJamesServer server) {
        assertThat(server.getProbe(LuceneProbe.class).searchHighlighter)
            .isInstanceOf(LuceneSearchHighlighter.class);
    }
}
