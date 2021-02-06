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

package org.apache.james.mailrepository.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryMailRepositoryStoreTest {
    private static final MailRepositoryUrl MEMORY1_REPO = MailRepositoryUrl.from("memory1://repo");
    private static final MailRepositoryUrl UNKNOWN_PROTOCOL_REPO = MailRepositoryUrl.from("toto://repo");
    private static final MailRepositoryUrl MEMORY2_REPO = MailRepositoryUrl.from("memory2://repo");
    private static final MailRepositoryPath PATH_REPO = MailRepositoryPath.from("repo");

    private MemoryMailRepositoryUrlStore urlStore;

    private SimpleMailRepositoryLoader loader;
    private MemoryMailRepositoryStore repositoryStore;
    private FileSystemImpl fileSystem;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        loader = new SimpleMailRepositoryLoader();
        configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        urlStore = new MemoryMailRepositoryUrlStore();

        MailRepositoryStoreConfiguration storeConfiguration = MailRepositoryStoreConfiguration.parse(
            new FileConfigurationProvider(fileSystem, configuration).getConfiguration("mailrepositorystore"));

        repositoryStore = new MemoryMailRepositoryStore(urlStore, loader, storeConfiguration);
        repositoryStore.init();
    }

    @Test
    void selectingANonRegisteredProtocolShouldFail() {
        assertThatThrownBy(() -> repositoryStore.select(MailRepositoryUrl.from("proto://repo")))
            .isInstanceOf(MailRepositoryStore.UnsupportedRepositoryStoreException.class);
    }

    @Test
    void selectingARegisteredProtocolShouldWork() {
        assertThat(repositoryStore.select(MEMORY1_REPO))
            .isInstanceOf(MemoryMailRepository.class);
    }

    @Test
    void selectingTwiceARegisteredProtocolWithSameDestinationShouldReturnTheSameResult() {
        assertThat(repositoryStore.select(MEMORY1_REPO))
            .isEqualTo(repositoryStore.select(MEMORY1_REPO));
    }

    @Test
    void selectingTwiceARegisteredProtocolWithDifferentDestinationShouldReturnDifferentResults() {
        assertThat(repositoryStore.select(MEMORY1_REPO))
            .isNotEqualTo(repositoryStore.select(MailRepositoryUrl.from("memory1://repo1")));
    }

    @Test
    void configureShouldThrowWhenNonValidClassesAreProvided() throws Exception {
        MailRepositoryStoreConfiguration storeConfiguration = MailRepositoryStoreConfiguration.parse(
            new FileConfigurationProvider(fileSystem, configuration).getConfiguration("fakemailrepositorystore"));

        repositoryStore = new MemoryMailRepositoryStore(urlStore, loader, storeConfiguration);

        repositoryStore.init();

        assertThatThrownBy(() -> repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(
            new Protocol("memory"), MailRepositoryPath.from("/var/will/fail"))))
            .isInstanceOf(MailRepositoryStore.MailRepositoryStoreException.class);
    }

    @Test
    void configureShouldNotThrowOnEmptyConfiguration() throws Exception {
        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.parse(new BaseHierarchicalConfiguration());

        repositoryStore = new MemoryMailRepositoryStore(urlStore, loader, configuration);

        repositoryStore.init();
    }

    @Test
    void getUrlsShouldBeEmptyIfNoSelectWerePerformed() {
        assertThat(repositoryStore.getUrls()).isEmpty();
    }

    @Test
    void getUrlsShouldReturnUsedUrls() {
        MailRepositoryUrl url1 = MailRepositoryUrl.from("memory1://repo1");
        MailRepositoryUrl url2 = MailRepositoryUrl.from("memory1://repo2");
        MailRepositoryUrl url3 = MailRepositoryUrl.from("memory1://repo3");
        repositoryStore.select(url1);
        repositoryStore.select(url2);
        repositoryStore.select(url3);
        assertThat(repositoryStore.getUrls()).containsOnly(url1, url2, url3);
    }

    @Test
    void getUrlsResultsShouldNotBeDuplicated() {
        repositoryStore.select(MEMORY1_REPO);
        repositoryStore.select(MEMORY1_REPO);
        assertThat(repositoryStore.getUrls()).containsExactly(MEMORY1_REPO);
    }

    @Test
    void getPathsShouldBeEmptyIfNoSelectWerePerformed() {
        assertThat(repositoryStore.getPaths()).isEmpty();
    }

    @Test
    void getPathsShouldReturnUsedUrls() {
        MailRepositoryPath path1 = MailRepositoryPath.from("repo1");
        MailRepositoryPath path2 = MailRepositoryPath.from("repo1");
        MailRepositoryPath path3 = MailRepositoryPath.from("repo1");
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path1, "memory1"));
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path2, "memory1"));
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path3, "memory1"));
        assertThat(repositoryStore.getPaths()).containsOnly(path1, path2, path3);
    }

    @Test
    void getPathsResultsShouldNotBeDuplicatedWithTheSameProtocol() {
        repositoryStore.select(MEMORY1_REPO);
        repositoryStore.select(MEMORY1_REPO);
        assertThat(repositoryStore.getPaths()).containsExactly(PATH_REPO);
    }

    @Test
    void getPathsResultsShouldNotBeDuplicatedWithDifferentProtocols() {
        repositoryStore.select(MEMORY1_REPO);
        repositoryStore.select(MEMORY2_REPO);
        assertThat(repositoryStore.getPaths()).containsExactly(PATH_REPO);
    }

    @Test
    void getShouldReturnEmptyWhenUrlNotInUse() {
        assertThat(repositoryStore.get(MEMORY1_REPO))
            .isEmpty();
    }

    @Test
    void getShouldReturnRepositoryWhenUrlExists() {
        urlStore.add(MEMORY1_REPO);

        assertThat(repositoryStore.get(MEMORY1_REPO))
            .isNotEmpty();
    }

    @Test
    void getByPathShouldReturnRepositoryWhenUrlExists() {
        urlStore.add(MEMORY1_REPO);

        assertThat(repositoryStore.getByPath(MEMORY1_REPO.getPath()))
            .isNotEmpty();
    }

    @Test
    void getShouldReturnPreviouslyCreatedMailRepository() {
        MailRepository mailRepository = repositoryStore.select(MEMORY1_REPO);

        assertThat(repositoryStore.get(MEMORY1_REPO))
            .contains(mailRepository);
    }

    @Test
    void getByPathShouldReturnEmptyWhenUrlNotInUse() {
        assertThat(repositoryStore.getByPath(PATH_REPO))
            .isEmpty();
    }

    @Test
    void getByPathShouldReturnPreviouslyCreatedMatchingMailRepository() {
        MailRepository mailRepository = repositoryStore.select(MEMORY1_REPO);

        assertThat(repositoryStore.getByPath(PATH_REPO))
            .contains(mailRepository);
    }

    @Test
    void getByPathShouldReturnPreviouslyCreatedMatchingMailRepositories() {
        MailRepository mailRepositoryFile = repositoryStore.select(MEMORY1_REPO);
        MailRepository mailRepositoryArbitrary = repositoryStore.select(MEMORY2_REPO);

        assertThat(repositoryStore.getByPath(PATH_REPO))
            .contains(mailRepositoryFile)
            .contains(mailRepositoryArbitrary);
    }

    @Test
    void getByPathShouldReturnEmptyWhenNoMailRepositoriesAreMatching() {
        repositoryStore.select(MEMORY1_REPO);

        assertThat(repositoryStore.getByPath(MailRepositoryPath.from("unknown")))
            .isEmpty();
    }

    @Test
    void selectShouldNotReturnDifferentResultsWhenUsedInAConcurrentEnvironment() throws Exception {
        MailRepositoryUrl url = MailRepositoryUrl.from("memory1://repo");
        int threadCount = 10;

        ConcurrentTestRunner.builder()
            .operation((threadNb, operationNb) -> repositoryStore.select(url)
                .store(FakeMail.builder()
                    .name("name" + threadNb)
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setText("Any body"))
                    .build()))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        long actualSize = repositoryStore.get(url).get().size();

        assertThat(actualSize).isEqualTo(threadCount);
    }

    @Test
    void selectShouldNotAddUrlWhenProtocolDoNotExist() {
        assertThatThrownBy(() -> repositoryStore.select(UNKNOWN_PROTOCOL_REPO));

        assertThat(urlStore.listDistinct()).isEmpty();
    }
}
