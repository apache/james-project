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

package org.apache.james.xodus;

import java.io.IOException;

import org.apache.james.mailbox.lucene.search.LuceneMessageSearchIndex;
import org.apache.james.mailbox.lucene.search.LuceneSearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchHighlighterConfiguration;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

import jetbrains.exodus.env.Environment;
import jetbrains.exodus.lucene2.XodusDirectory;

public class XodusLuceneSearchMailboxModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusLuceneSearchMailboxModule.class);

    @Singleton
    public static class XodusDirectoryHolder implements java.io.Closeable {
        private final Directory directory;

        @com.google.inject.Inject
        public XodusDirectoryHolder(Environment environment) throws IOException {
            LOGGER.info("Opening Xodus Lucene Directory v2 backed by Xodus Environment");
            this.directory = new XodusDirectory(environment);
        }

        public Directory getDirectory() {
            return directory;
        }

        @Override
        @jakarta.annotation.PreDestroy
        public void close() {
            LOGGER.info("Closing Lucene Directory");
            try {
                if (directory != null) {
                    directory.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to close Lucene Directory", e);
            }
        }
    }

    @Override
    protected void configure() {
        bind(XodusDirectoryHolder.class).asEagerSingleton();
        bind(SearchHighlighter.class).to(LuceneSearchHighlighter.class).in(Scopes.SINGLETON);
        bind(LuceneMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(LuceneMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(LuceneMessageSearchIndex.class);

        Multibinder.newSetBinder(binder(), org.apache.james.events.EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(LuceneMessageSearchIndex.class);
    }

    @Provides
    @Singleton
    Directory provideDirectory(XodusDirectoryHolder holder) {
        return holder.getDirectory();
    }

    @Provides
    @Singleton
    SearchHighlighterConfiguration provideSearchHighlighterConfiguration() {
        return SearchHighlighterConfiguration.DEFAULT;
    }
}
