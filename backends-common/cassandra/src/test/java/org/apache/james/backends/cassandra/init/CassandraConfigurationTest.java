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

package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CassandraConfigurationTest {
    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void cassandraConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraConfiguration.class)
            .verify();
    }

    @Test
    public void defaultBuilderShouldConstructDefaultConfiguration() {
        assertThat(CassandraConfiguration.builder().build())
            .isEqualTo(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    public void aclMaxRetryShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .aclMaxRetry(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void aclMaxRetryShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
            .aclMaxRetry(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void expungeChunkSizeShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .expungeChunkSize(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void expungeChunkSizeShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .expungeChunkSize(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void messageReadChunkSizeShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .messageReadChunkSize(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void messageReadChunkSizeShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .messageReadChunkSize(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateChunkSizeShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateChunkSize(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateChunkSizeShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateChunkSize(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateMessageIdMaxRetryShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateMessageIdMaxRetry(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateMessageIdMaxRetryShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateMessageIdMaxRetry(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateMessageMaxRetryShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateMessageMaxRetry(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void flagsUpdateMessageMaxRetryShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .flagsUpdateMessageMaxRetry(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fetchNextPageInAdvanceRowShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .fetchNextPageInAdvanceRow(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fetchNextPageInAdvanceRowShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .fetchNextPageInAdvanceRow(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void modSeqMaxRetryShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .modSeqMaxRetry(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void modSeqMaxRetryShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .modSeqMaxRetry(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void uidMaxRetryShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .uidMaxRetry(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void uidMaxRetryShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .uidMaxRetry(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void attachmentV2MigrationReadTimeoutShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .attachmentV2MigrationReadTimeout(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void attachmentV2MigrationReadTimeoutShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .attachmentV2MigrationReadTimeout(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void messageAttachmentIdsReadTimeoutShouldThrowOnZero() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .messageAttachmentIdsReadTimeout(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void messageAttachmentIdsReadTimeoutShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> CassandraConfiguration.builder()
                .messageAttachmentIdsReadTimeout(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void builderShouldCreateTheRightObject() {
        int aclMaxRetry = 1;
        int modSeqMaxRetry = 2;
        int uidMaxRetry = 3;
        int fetchNextPageInAdvanceRow = 4;
        int flagsUpdateMessageMaxRetry = 5;
        int flagsUpdateMessageIdMaxRetry = 6;
        int flagsUpdateChunkSize = 7;
        int messageReadChunkSize = 8;
        int expungeChunkSize = 9;
        int blobPartSize = 10;
        int attachmentV2MigrationReadTimeout = 11;
        int messageAttachmentIdReadTimeout = 12;

        CassandraConfiguration configuration = CassandraConfiguration.builder()
            .aclMaxRetry(aclMaxRetry)
            .modSeqMaxRetry(modSeqMaxRetry)
            .uidMaxRetry(uidMaxRetry)
            .fetchNextPageInAdvanceRow(fetchNextPageInAdvanceRow)
            .flagsUpdateMessageMaxRetry(flagsUpdateMessageMaxRetry)
            .flagsUpdateMessageIdMaxRetry(flagsUpdateMessageIdMaxRetry)
            .flagsUpdateChunkSize(flagsUpdateChunkSize)
            .messageReadChunkSize(messageReadChunkSize)
            .expungeChunkSize(expungeChunkSize)
            .blobPartSize(blobPartSize)
            .attachmentV2MigrationReadTimeout(attachmentV2MigrationReadTimeout)
            .messageAttachmentIdsReadTimeout(messageAttachmentIdReadTimeout)
            .build();

        softly.assertThat(configuration.getAclMaxRetry()).isEqualTo(aclMaxRetry);
        softly.assertThat(configuration.getModSeqMaxRetry()).isEqualTo(modSeqMaxRetry);
        softly.assertThat(configuration.getUidMaxRetry()).isEqualTo(uidMaxRetry);
        softly.assertThat(configuration.getFetchNextPageInAdvanceRow()).isEqualTo(fetchNextPageInAdvanceRow);
        softly.assertThat(configuration.getFlagsUpdateMessageMaxRetry()).isEqualTo(flagsUpdateMessageMaxRetry);
        softly.assertThat(configuration.getFlagsUpdateMessageIdMaxRetry()).isEqualTo(flagsUpdateMessageIdMaxRetry);
        softly.assertThat(configuration.getFlagsUpdateChunkSize()).isEqualTo(flagsUpdateChunkSize);
        softly.assertThat(configuration.getMessageReadChunkSize()).isEqualTo(messageReadChunkSize);
        softly.assertThat(configuration.getExpungeChunkSize()).isEqualTo(expungeChunkSize);
        softly.assertThat(configuration.getBlobPartSize()).isEqualTo(blobPartSize);
        softly.assertThat(configuration.getAttachmentV2MigrationReadTimeout()).isEqualTo(attachmentV2MigrationReadTimeout);
        softly.assertThat(configuration.getMessageAttachmentIdsReadTimeout()).isEqualTo(messageAttachmentIdReadTimeout);
    }

}
