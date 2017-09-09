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

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CassandraConfigurationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void cassandraConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraConfiguration.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void defaultBuilderShouldConstructDefaultConfiguration() {
        assertThat(CassandraConfiguration.builder().build())
            .isEqualTo(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    public void aclMaxRetryShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .aclMaxRetry(-1);
    }

    @Test
    public void aclMaxRetryShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .aclMaxRetry(0);
    }

    @Test
    public void expungeChunkSizeShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .expungeChunkSize(-1);
    }

    @Test
    public void expungeChunkSizeShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .expungeChunkSize(0);
    }

    @Test
    public void messageReadChunkSizeShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .messageReadChunkSize(-1);
    }

    @Test
    public void messageReadChunkSizeShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .messageReadChunkSize(0);
    }

    @Test
    public void flagsUpdateChunkSizeShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateChunkSize(-1);
    }

    @Test
    public void flagsUpdateChunkSizeShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateChunkSize(0);
    }

    @Test
    public void flagsUpdateMessageIdMaxRetryShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateMessageIdMaxRetry(-1);
    }

    @Test
    public void flagsUpdateMessageIdMaxRetryShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateMessageIdMaxRetry(0);
    }

    @Test
    public void flagsUpdateMessageMaxRetryShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateMessageMaxRetry(-1);
    }

    @Test
    public void flagsUpdateMessageMaxRetryShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .flagsUpdateMessageMaxRetry(0);
    }

    @Test
    public void fetchNextPageInAdvanceRowShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .fetchNextPageInAdvanceRow(-1);
    }

    @Test
    public void fetchNextPageInAdvanceRowShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .fetchNextPageInAdvanceRow(0);
    }

    @Test
    public void modSeqMaxRetryShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .modSeqMaxRetry(-1);
    }

    @Test
    public void modSeqMaxRetryShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .modSeqMaxRetry(0);
    }

    @Test
    public void uidMaxRetryShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .uidMaxRetry(-1);
    }

    @Test
    public void uidMaxRetryShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .uidMaxRetry(0);
    }

    @Test
    public void attachmentV2MigrationReadTimeoutShouldThrowOnZero() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .attachmentV2MigrationReadTimeout(0);
    }

    @Test
    public void attachmentV2MigrationReadTimeoutShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        CassandraConfiguration.builder()
            .attachmentV2MigrationReadTimeout(-1);
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
    }

}
