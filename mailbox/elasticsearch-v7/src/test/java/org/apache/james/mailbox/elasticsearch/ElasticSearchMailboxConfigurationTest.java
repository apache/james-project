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

package org.apache.james.mailbox.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.WriteAliasName;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ElasticSearchMailboxConfigurationTest {
    @Test
    void elasticSearchMailboxConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(ElasticSearchMailboxConfiguration.class)
            .verify();
    }

    @Test
    void getIndexMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.name", "other");
        configuration.addProperty("elasticsearch.index.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    @Test
    void getReadAliasMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.mailbox.name", name);
        configuration.addProperty("elasticsearch.alias.read.name", "other");
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS);
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.mailbox.name", name);
        configuration.addProperty("elasticsearch.alias.write.name", "other");
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
    }

    @Test
    void getIndexAttachmentShouldReturnConfiguredValueWhenTrue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", true);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }

    @Test
    void getIndexAttachmentShouldReturnConfiguredValueWhenFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", false);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.NO);
    }

    @Test
    void getIndexAttachmentShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchMailboxConfiguration elasticSearchConfiguration = ElasticSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }

}