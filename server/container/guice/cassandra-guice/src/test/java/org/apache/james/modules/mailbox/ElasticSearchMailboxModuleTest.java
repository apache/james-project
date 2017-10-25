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

import static org.apache.james.modules.mailbox.ElasticSearchMailboxModule.ELASTICSEARCH_HOSTS;
import static org.apache.james.modules.mailbox.ElasticSearchMailboxModule.ELASTICSEARCH_MASTER_HOST;
import static org.apache.james.modules.mailbox.ElasticSearchMailboxModule.ELASTICSEARCH_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.IndexName;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ElasticSearchMailboxModuleTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void provideIndexNameShouldRetrievedConfiguredIndexName() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.name", name);

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexName indexName = testee.provideIndexName(() -> configuration);

        assertThat(indexName)
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void provideIndexNameShouldReturnDefaultIndexNameWhenNone() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexName indexName = testee.provideIndexName(() -> configuration);

        assertThat(indexName)
            .isEqualTo(MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    @Test
    public void provideIndexNameShouldReturnDefaultIndexNameWhenError() throws ConfigurationException {
        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexName indexName = testee.provideIndexName(() -> {
            throw new FileNotFoundException();
        });

        assertThat(indexName)
            .isEqualTo(MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    @Test
    public void provideAliasNameShouldRetrievedConfiguredAliasName() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.name", name);

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        AliasName indexName = testee.provideAliasName(() -> configuration);

        assertThat(indexName)
            .isEqualTo(new AliasName(name));
    }

    @Test
    public void provideAliasNameShouldReturnDefaultAliasNameWhenNone() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        AliasName aliasName = testee.provideAliasName(() -> configuration);

        assertThat(aliasName)
            .isEqualTo(MailboxElasticsearchConstants.DEFAULT_MAILBOX_ALIAS);
    }

    @Test
    public void provideAliasNameShouldReturnDefaultAliasNameWhenError() throws ConfigurationException {
        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        AliasName aliasName = testee.provideAliasName(() -> {
            throw new FileNotFoundException();
        });

        assertThat(aliasName)
            .isEqualTo(MailboxElasticsearchConstants.DEFAULT_MAILBOX_ALIAS);
    }

    @Test
    public void provideIndexAttachmentsShouldReturnTrueWhenIndexAttachmentsIsTrueInConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", true);

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexAttachments indexAttachments = testee.provideIndexAttachments(configuration);

        assertThat(indexAttachments).isEqualTo(IndexAttachments.YES);
    }

    @Test
    public void provideIndexAttachmentsShouldReturnFalseWhenIndexAttachmentsIsFalseInConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", false);

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexAttachments indexAttachments = testee.provideIndexAttachments(configuration);

        assertThat(indexAttachments).isEqualTo(IndexAttachments.NO);
    }

    @Test
    public void provideIndexAttachmentsShouldReturnTrueWhenIndexAttachmentsIsNotDefinedInConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        ElasticSearchMailboxModule testee = new ElasticSearchMailboxModule();

        IndexAttachments indexAttachments = testee.provideIndexAttachments(configuration);

        assertThat(indexAttachments).isEqualTo(IndexAttachments.YES);
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenNoHostSpecify() throws Exception {
        expectedException.expect(ConfigurationException.class);
        expectedException.expectMessage("You should specify either (" + ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + ") or " + ELASTICSEARCH_HOSTS);

        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoAndMultiHostSpecified() throws Exception {
        expectedException.expect(ConfigurationException.class);
        expectedException.expectMessage("You should choose between mono host set up and " + ELASTICSEARCH_HOSTS);

        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.of(9200),
            Optional.of("localhost:9200"));
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutPort() throws Exception {
        expectedException.expect(ConfigurationException.class);
        expectedException.expectMessage(ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + " should be specified together");

        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.empty(),
            Optional.empty());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutAddress() throws Exception {
        expectedException.expect(ConfigurationException.class);
        expectedException.expectMessage(ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + " should be specified together");

        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.of(9200),
            Optional.empty());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMonoHostConfiguration() throws Exception {
        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.of(9200),
            Optional.empty());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMultiHostConfiguration() throws Exception {
        ElasticSearchMailboxModule.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.empty(),
            Optional.of("localhost:9200"));
    }
}
