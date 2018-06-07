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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.file.FileMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.modules.server.MailStoreRepositoryModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class InMemoryMailRepositoryStoreTest {

    private static class MemoryMailRepositoryProvider implements MailRepositoryProvider {
        @Override
        public String canonicalName() {
            return MemoryMailRepository.class.getCanonicalName();
        }

        @Override
        public MailRepository provide(String url) {
            return new MemoryMailRepository();
        }
    }

    private InMemoryMailRepositoryStore repositoryStore;
    private FileSystemImpl fileSystem;
    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        repositoryStore = new InMemoryMailRepositoryStore(Sets.newHashSet(
                new MailStoreRepositoryModule.FileMailRepositoryProvider(
                        fileSystem),
                new MemoryMailRepositoryProvider()));
        repositoryStore.configure(new FileConfigurationProvider(fileSystem, configuration)
            .getConfiguration("mailrepositorystore"));
        repositoryStore.init();
    }

    @Test(expected = MailRepositoryStore.MailRepositoryStoreException.class)
    public void selectingANonRegisteredProtocolShouldFail() throws Exception {
        repositoryStore.select("proto://repo");
    }

    @Test
    public void selectingARegisteredProtocolShouldWork() throws Exception {
        assertThat(repositoryStore.select("file://repo")).isInstanceOf(FileMailRepository.class);
    }

    @Test
    public void selectingTwiceARegisteredProtocolWithSameDestinationShouldReturnTheSameResult() throws Exception {
        assertThat(repositoryStore.select("file://repo")).isEqualTo(repositoryStore.select("file://repo"));
    }

    @Test
    public void selectingTwiceARegisteredProtocolWithDifferentDestinationShouldReturnDifferentResults() throws Exception {
        assertThat(repositoryStore.select("file://repo")).isNotEqualTo(repositoryStore.select("file://repo1"));
    }

    @Test
    public void configureShouldThrowWhenNonValidClassesAreProvided() throws Exception {
        repositoryStore = new InMemoryMailRepositoryStore(Sets.newHashSet(
            new MailStoreRepositoryModule.FileMailRepositoryProvider(
                fileSystem)));
        repositoryStore.configure(new FileConfigurationProvider(fileSystem, configuration).getConfiguration("fakemailrepositorystore"));

        assertThatThrownBy(() -> repositoryStore.init())
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    public void configureShouldNotThrowOnEmptyConfiguration() throws Exception {
        repositoryStore = new InMemoryMailRepositoryStore(Sets.newHashSet(
            new MailStoreRepositoryModule.FileMailRepositoryProvider(
                fileSystem)));
        repositoryStore.configure(new HierarchicalConfiguration());

        repositoryStore.init();
    }

    @Test
    public void getUrlsShouldBeEmptyIfNoSelectWerePerformed() {
        assertThat(repositoryStore.getUrls()).isEmpty();
    }

    @Test
    public void getUrlsShouldReturnUsedUrls() throws Exception {
        String url1 = "file://repo1";
        String url2 = "file://repo2";
        String url3 = "file://repo3";
        repositoryStore.select(url1);
        repositoryStore.select(url2);
        repositoryStore.select(url3);
        assertThat(repositoryStore.getUrls()).containsOnly(url1, url2, url3);
    }

    @Test
    public void getUrlsResultsShouldNotBeDuplicated() throws Exception {
        String url1 = "file://repo1";
        repositoryStore.select(url1);
        repositoryStore.select(url1);
        assertThat(repositoryStore.getUrls()).containsExactly(url1);
    }

    @Test
    public void getShouldReturnEmptyWhenUrlNotInUse() {
        assertThat(repositoryStore.get("file://repo"))
            .isEmpty();
    }

    @Test
    public void getShouldReturnPreviouslyCreatedMailRepository() throws Exception {
        String url = "file://repo";
        MailRepository mailRepository = repositoryStore.select(url);

        assertThat(repositoryStore.get(url))
            .contains(mailRepository);
    }

    @Test
    public void selectShouldNotReturnDifferentResultsWhenUsedInAConcurrentEnvironment() throws Exception {
        String url = "memory://repo";
        int threadCount = 10;
        int operationCount = 1;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNb, operationNb) -> repositoryStore.select(url)
                .store(FakeMail.builder()
                    .name("name" + threadNb)
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setText("Any body"))
                    .build()));
        concurrentTestRunner.run().awaitTermination(1, TimeUnit.MINUTES);
        concurrentTestRunner.assertNoException();

        long actualSize = repositoryStore.get(url).get().size();

        assertThat(actualSize).isEqualTo(threadCount);
    }

}
