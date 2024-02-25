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

package org.apache.james.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class OpenSearchMailboxConfigurationTest {
    @Test
    void openSearchMailboxConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(OpenSearchMailboxConfiguration.class)
            .verify();
    }

    @Test
    void getIndexMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.index.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.index.name", "other");
        configuration.addProperty("opensearch.index.mailbox.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.index.mailbox.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexMailboxName())
            .isEqualTo(MailboxOpenSearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    @Test
    void getReadAliasMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.read.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.read.mailbox.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.read.mailbox.name", name);
        configuration.addProperty("opensearch.alias.read.name", "other");
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(MailboxOpenSearchConstants.DEFAULT_MAILBOX_READ_ALIAS);
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnOldConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.write.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.write.mailbox.name", name);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("opensearch.alias.write.mailbox.name", name);
        configuration.addProperty("opensearch.alias.write.name", "other");
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasMailboxNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(MailboxOpenSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
    }

    @Test
    void getIndexAttachmentShouldReturnConfiguredValueWhenTrue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.indexAttachments", true);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }

    @Test
    void getIndexAttachmentShouldReturnConfiguredValueWhenFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.indexAttachments", false);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.NO);
    }

    @Test
    void getIndexAttachmentShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }

    @Test
    void getIndexBodyShouldReturnConfiguredValueWhenTrue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.indexBody", true);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexBody())
            .isEqualTo(IndexBody.YES);
    }

    @Test
    void getIndexBodyShouldReturnConfiguredValueWhenFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.indexBody", false);
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexBody())
            .isEqualTo(IndexBody.NO);
    }

    @Test
    void getIndexBodyShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.hosts", "127.0.0.1");

        OpenSearchMailboxConfiguration openSearchConfiguration = OpenSearchMailboxConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexBody())
            .isEqualTo(IndexBody.YES);
    }

}