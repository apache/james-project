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

package org.apache.james.backends.cassandra.init.configuration;

import static java.lang.Math.toIntExact;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CassandraConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraConfiguration.class);

    public static final float DEFAULT_MAILBOX_READ_REPAIR = 0.1f;
    public static final float DEFAULT_MAX_MAILBOX_COUNTERS_READ_REPAIR_CHANCE = 0.1f;
    public static final float DEFAULT_ONE_HUNDRED_MAILBOX_COUNTERS_READ_REPAIR_CHANCE = 0.01f;
    public static final int DEFAULT_EXPUNGE_BATCH_SIZE = 50;
    public static final int DEFAULT_FLAGS_UPDATE_MESSAGE_MAX_RETRY = 1000;
    public static final int DEFAULT_FLAGS_UPDATE_MESSAGE_ID_MAX_RETRY = 1000;
    public static final int DEFAULT_MODSEQ_MAX_RETRY = 100000;
    public static final int DEFAULT_UID_MAX_RETRY = 100000;
    public static final int DEFAULT_ACL_MAX_RETRY = 1000;
    public static final int DEFAULT_FETCH_NEXT_PAGE_ADVANCE_IN_ROW = 100;
    public static final int DEFAULT_BLOB_PART_SIZE = 100 * 1024;
    public static final int DEFAULT_ATTACHMENT_V2_MIGRATION_READ_TIMEOUT = toIntExact(TimeUnit.HOURS.toMillis(1));
    public static final int DEFAULT_MESSAGE_ATTACHMENT_ID_MIGRATION_READ_TIMEOUT = toIntExact(TimeUnit.HOURS.toMillis(1));
    public static final String DEFAULT_CONSISTENCY_LEVEL_REGULAR = "QUORUM";
    public static final String DEFAULT_CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION = "SERIAL";
    public static final List<String> VALID_CONSISTENCY_LEVEL_REGULAR = ImmutableList.of("QUORUM", "LOCAL_QUORUM", "EACH_QUORUM");
    public static final List<String> VALID_CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION = ImmutableList.of("SERIAL", "LOCAL_SERIAL");
    public static final boolean DEFAULT_STRONG_CONSISTENCY = true;
    public static final boolean DEFAULT_OPTIMISTIC_CONSISTENCY_LEVEL = false;
    public static final boolean DEFAULT_MAIL_REPOSITORY_STRONG_CONSISTENCY = true;

    private static final String MAILBOX_READ_REPAIR = "mailbox.read.repair.chance";
    private static final String MAILBOX_MAX_COUNTERS_READ_REPAIR = "mailbox.counters.read.repair.chance.max";
    private static final String MAILBOX_ONE_HUNDRED_COUNTERS_READ_REPAIR = "mailbox.counters.read.repair.chance.one.hundred";
    private static final String MAILBOX_MAX_RETRY_ACL = "mailbox.max.retry.acl";
    private static final String MAILBOX_MAX_RETRY_MODSEQ = "mailbox.max.retry.modseq";
    private static final String MAILBOX_MAX_RETRY_UID = "mailbox.max.retry.uid";
    private static final String MAILBOX_MAX_RETRY_MESSAGE_FLAGS_UPDATE = "mailbox.max.retry.message.flags.update";
    private static final String MAILBOX_MAX_RETRY_MESSAGE_ID_FLAGS_UPDATE = "mailbox.max.retry.message.id.flags.update";
    private static final String FETCH_ADVANCE_ROW_COUNT = "fetch.advance.row.count";
    private static final String CHUNK_SIZE_EXPUNGE = "chunk.size.expunge";
    private static final String BLOB_PART_SIZE = "mailbox.blob.part.size";
    private static final String ATTACHMENT_V2_MIGRATION_READ_TIMEOUT = "attachment.v2.migration.read.timeout";
    private static final String MESSAGE_ATTACHMENTID_READ_TIMEOUT = "message.attachmentids.read.timeout";
    private static final String MAILBOX_READ_STRONG_CONSISTENCY = "mailbox.read.strong.consistency";
    private static final String MESSAGE_READ_STRONG_CONSISTENCY = "message.read.strong.consistency";
    private static final String MESSAGE_WRITE_STRONG_CONSISTENCY = "message.write.strong.consistency.unsafe";
    private static final String UID_READ_STRONG_CONSISTENCY = "uid.read.strong.consistency.unsafe";
    private static final String MODSEQ_READ_STRONG_CONSISTENCY = "modseq.read.strong.consistency.unsafe";
    private static final String CONSISTENCY_LEVEL_REGULAR = "cassandra.consistency_level.regular";
    private static final String CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION = "cassandra.consistency_level.lightweight_transaction";
    private static final String OPTIMISTIC_CONSISTENCY_LEVEL = "optimistic.consistency.level.enabled";
    private static final String MAIL_REPOSITORY_STRONG_CONSISTENCY = "mailrepository.strong.consistency";
    private static final String ACL_ENABLED = "acl.enabled";
    private static final String UID_MODSEQ_INCREMENT = "uid.modseq.increment";

    public static final CassandraConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static class Builder {
        private Optional<Integer> expungeChunkSize = Optional.empty();
        private Optional<Integer> flagsUpdateMessageIdMaxRetry = Optional.empty();
        private Optional<Integer> flagsUpdateMessageMaxRetry = Optional.empty();
        private Optional<Integer> modSeqMaxRetry = Optional.empty();
        private Optional<Integer> uidMaxRetry = Optional.empty();
        private Optional<Integer> aclMaxRetry = Optional.empty();
        private Optional<Integer> fetchNextPageInAdvanceRow = Optional.empty();
        private Optional<Integer> blobPartSize = Optional.empty();
        private Optional<Integer> attachmentV2MigrationReadTimeout = Optional.empty();
        private Optional<Integer> messageAttachmentIdsReadTimeout = Optional.empty();
        private Optional<String> consistencyLevelRegular = Optional.empty();
        private Optional<String> consistencyLevelLightweightTransaction = Optional.empty();
        private Optional<Float> mailboxReadRepair = Optional.empty();
        private Optional<Float> mailboxCountersReadRepairMax = Optional.empty();
        private Optional<Float> mailboxCountersReadRepairChanceOneHundred = Optional.empty();
        private Optional<Boolean> mailboxReadStrongConsistency = Optional.empty();
        private Optional<Boolean> messageReadStrongConsistency = Optional.empty();
        private Optional<Boolean> messageWriteStrongConsistency = Optional.empty();
        private Optional<Boolean> uidReadStrongConsistency = Optional.empty();
        private Optional<Boolean> modseqReadStrongConsistency = Optional.empty();
        private Optional<Boolean> optimisticConsistencyLevel = Optional.empty();
        private Optional<Boolean> mailRepositoryStrongConsistency = Optional.empty();
        private Optional<Boolean> aclEnabled = Optional.empty();
        private Optional<Integer> uidModseqIncrement = Optional.empty();

        public Builder mailboxReadStrongConsistency(boolean value) {
            this.mailboxReadStrongConsistency = Optional.of(value);
            return this;
        }

        public Builder mailboxReadStrongConsistency(Optional<Boolean> value) {
            this.mailboxReadStrongConsistency = value;
            return this;
        }

        public Builder aclEnabled(Optional<Boolean> value) {
            this.aclEnabled = value;
            return this;
        }

        public Builder messageReadStrongConsistency(boolean value) {
            this.messageReadStrongConsistency = Optional.of(value);
            return this;
        }

        public Builder messageReadStrongConsistency(Optional<Boolean> value) {
            this.messageReadStrongConsistency = value;
            return this;
        }

        public Builder uidReadStrongConsistency(Optional<Boolean> value) {
            this.uidReadStrongConsistency = value;
            return this;
        }

        public Builder modseqReadStrongConsistency(Optional<Boolean> value) {
            this.modseqReadStrongConsistency = value;
            return this;
        }

        public Builder messageWriteStrongConsistency(boolean value) {
            this.messageWriteStrongConsistency = Optional.of(value);
            return this;
        }

        public Builder messageWriteStrongConsistency(Optional<Boolean> value) {
            this.messageWriteStrongConsistency = value;
            return this;
        }

        public Builder expungeChunkSize(int value) {
            Preconditions.checkArgument(value > 0, "expungeChunkSize needs to be strictly positive");
            this.expungeChunkSize = Optional.of(value);
            return this;
        }

        public Builder uidModseqIncrement(int value) {
            Preconditions.checkArgument(value > 0, "uidModseqIncrement needs to be strictly positive");
            this.uidModseqIncrement = Optional.of(value);
            return this;
        }

        public Builder uidModseqIncrement(Optional<Integer> value) {
            value.ifPresent(this::uidModseqIncrement);
            return this;
        }

        public Builder flagsUpdateMessageIdMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "flagsUpdateMessageIdMaxRetry needs to be strictly positive");
            this.flagsUpdateMessageIdMaxRetry = Optional.of(value);
            return this;
        }

        public Builder flagsUpdateMessageMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "flagsUpdateMessageMaxRetry needs to be strictly positive");
            this.flagsUpdateMessageMaxRetry = Optional.of(value);
            return this;
        }

        public Builder modSeqMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "modSeqMaxRetry needs to be strictly positive");
            this.modSeqMaxRetry = Optional.of(value);
            return this;
        }

        public Builder uidMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "uidMaxRetry needs to be strictly positive");
            this.uidMaxRetry = Optional.of(value);
            return this;
        }

        public Builder aclMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "aclMaxRetry needs to be strictly positive");
            this.aclMaxRetry = Optional.of(value);
            return this;
        }

        public Builder fetchNextPageInAdvanceRow(int value) {
            Preconditions.checkArgument(value > 0, "fetchNextPageInAdvanceRow needs to be strictly positive");
            this.fetchNextPageInAdvanceRow = Optional.of(value);
            return this;
        }

        public Builder blobPartSize(int value) {
            Preconditions.checkArgument(value > 0, "blobPartSize needs to be strictly positive");
            this.blobPartSize = Optional.of(value);
            return this;
        }

        public Builder attachmentV2MigrationReadTimeout(int value) {
            Preconditions.checkArgument(value > 0, "attachmentV2MigrationReadTimeout needs to be strictly positive");
            this.attachmentV2MigrationReadTimeout = Optional.of(value);
            return this;
        }

        public Builder messageAttachmentIdsReadTimeout(int value) {
            Preconditions.checkArgument(value > 0, "messageAttachmentIdsReadTimeout needs to be strictly positive");
            this.messageAttachmentIdsReadTimeout = Optional.of(value);
            return this;
        }

        public Builder mailboxReadRepair(float value) {
            Preconditions.checkArgument(value >= 0, "mailboxReadRepair needs to be positive");
            Preconditions.checkArgument(value <= 1, "mailboxReadRepair needs to be less or equal to 1");
            this.mailboxReadRepair = Optional.of(value);
            return this;
        }

        public Builder mailboxCountersReadRepairMax(float value) {
            Preconditions.checkArgument(value >= 0, "mailboxCountersReadRepairMax needs to be positive");
            Preconditions.checkArgument(value <= 1, "mailboxCountersReadRepairMax needs to be less or equal to 1");
            this.mailboxCountersReadRepairMax = Optional.of(value);
            return this;
        }

        public Builder mailboxCountersReadRepairChanceOneHundred(float value) {
            Preconditions.checkArgument(value >= 0, "mailboxCountersReadRepairChanceOneHundred needs to be positive");
            Preconditions.checkArgument(value <= 1, "mailboxCountersReadRepairChanceOneHundred needs to be less or equal to 1");
            this.mailboxCountersReadRepairChanceOneHundred = Optional.of(value);
            return this;
        }

        public Builder expungeChunkSize(Optional<Integer> value) {
            value.ifPresent(this::expungeChunkSize);
            return this;
        }

        public Builder flagsUpdateMessageIdMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::flagsUpdateMessageIdMaxRetry);
            return this;
        }

        public Builder flagsUpdateMessageMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::flagsUpdateMessageMaxRetry);
            return this;
        }

        public Builder modSeqMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::modSeqMaxRetry);
            return this;
        }

        public Builder uidMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::uidMaxRetry);
            return this;
        }

        public Builder aclMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::aclMaxRetry);
            return this;
        }

        public Builder fetchNextPageInAdvanceRow(Optional<Integer> value) {
            value.ifPresent(this::fetchNextPageInAdvanceRow);
            return this;
        }

        public Builder blobPartSize(Optional<Integer> value) {
            value.ifPresent(this::blobPartSize);
            return this;
        }

        public Builder attachmentV2MigrationReadTimeout(Optional<Integer> value) {
            value.ifPresent(this::attachmentV2MigrationReadTimeout);
            return this;
        }

        public Builder messageAttachmentIdsReadTimeout(Optional<Integer> value) {
            value.ifPresent(this::messageAttachmentIdsReadTimeout);
            return this;
        }

        public Builder mailboxReadRepair(Optional<Float> value) {
            value.ifPresent(this::mailboxReadRepair);
            return this;
        }

        public Builder mailboxCountersReadRepairMax(Optional<Float> value) {
            value.ifPresent(this::mailboxCountersReadRepairMax);
            return this;
        }

        public Builder mailboxCountersReadRepairChanceOneHundred(Optional<Float> value) {
            value.ifPresent(this::mailboxCountersReadRepairChanceOneHundred);
            return this;
        }

        public Builder consistencyLevelRegular(String value) {
            Preconditions.checkArgument(VALID_CONSISTENCY_LEVEL_REGULAR.contains(value),
                "consistencyLevelRegular needs to be one of the following: " + String.join(", ", VALID_CONSISTENCY_LEVEL_REGULAR));
            this.consistencyLevelRegular = Optional.of(value);
            return this;
        }

        public Builder consistencyLevelLightweightTransaction(String value) {
            Preconditions.checkArgument(VALID_CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION.contains(value),
                "consistencyLevelLightweightTransaction needs to be one of the following: " + String.join(", ", VALID_CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION));
            this.consistencyLevelLightweightTransaction = Optional.of(value);
            return this;
        }

        public Builder consistencyLevelRegular(Optional<String> value) {
            value.ifPresent(this::consistencyLevelRegular);
            return this;
        }

        public Builder consistencyLevelLightweightTransaction(Optional<String> value) {
            value.ifPresent(this::consistencyLevelLightweightTransaction);
            return this;
        }

        public Builder optimisticConsistencyLevel(boolean value) {
            this.optimisticConsistencyLevel = Optional.of(value);
            return this;
        }

        public Builder optimisticConsistencyLevel(Optional<Boolean> value) {
            this.optimisticConsistencyLevel = value;
            return this;
        }

        public Builder mailRepositoryStrongConsistency(Optional<Boolean> value) {
            this.mailRepositoryStrongConsistency = value;
            return this;
        }

        public CassandraConfiguration build() {
            String consistencyLevelRegular = this.consistencyLevelRegular.orElse(DEFAULT_CONSISTENCY_LEVEL_REGULAR);
            String consistencyLevelLightweightTransaction = this.consistencyLevelLightweightTransaction.orElse(DEFAULT_CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION);
            Predicate<String> isLocal = consistencyLevel -> consistencyLevel.startsWith("LOCAL_");
            if (isLocal.test(consistencyLevelRegular) != isLocal.test(consistencyLevelLightweightTransaction)) {
                LOGGER.warn("The consistency levels may not be properly configured, one is local and the other not: "
                    + "regular = '{}' and lightweight transaction = '{}'", consistencyLevelRegular, consistencyLevelLightweightTransaction);
            }

            return new CassandraConfiguration(aclMaxRetry.orElse(DEFAULT_ACL_MAX_RETRY),
                expungeChunkSize.orElse(DEFAULT_EXPUNGE_BATCH_SIZE),
                flagsUpdateMessageIdMaxRetry.orElse(DEFAULT_FLAGS_UPDATE_MESSAGE_ID_MAX_RETRY),
                flagsUpdateMessageMaxRetry.orElse(DEFAULT_FLAGS_UPDATE_MESSAGE_MAX_RETRY),
                modSeqMaxRetry.orElse(DEFAULT_MODSEQ_MAX_RETRY),
                uidMaxRetry.orElse(DEFAULT_UID_MAX_RETRY),
                fetchNextPageInAdvanceRow.orElse(DEFAULT_FETCH_NEXT_PAGE_ADVANCE_IN_ROW),
                blobPartSize.orElse(DEFAULT_BLOB_PART_SIZE),
                attachmentV2MigrationReadTimeout.orElse(DEFAULT_ATTACHMENT_V2_MIGRATION_READ_TIMEOUT),
                messageAttachmentIdsReadTimeout.orElse(DEFAULT_MESSAGE_ATTACHMENT_ID_MIGRATION_READ_TIMEOUT),
                consistencyLevelRegular,
                consistencyLevelLightweightTransaction,
                mailboxReadRepair.orElse(DEFAULT_MAILBOX_READ_REPAIR),
                mailboxCountersReadRepairMax.orElse(DEFAULT_MAX_MAILBOX_COUNTERS_READ_REPAIR_CHANCE),
                mailboxCountersReadRepairChanceOneHundred.orElse(DEFAULT_ONE_HUNDRED_MAILBOX_COUNTERS_READ_REPAIR_CHANCE),
                mailboxReadStrongConsistency.orElse(DEFAULT_STRONG_CONSISTENCY),
                messageReadStrongConsistency.orElse(DEFAULT_STRONG_CONSISTENCY),
                messageWriteStrongConsistency.orElse(DEFAULT_STRONG_CONSISTENCY),
                optimisticConsistencyLevel.orElse(DEFAULT_OPTIMISTIC_CONSISTENCY_LEVEL),
                mailRepositoryStrongConsistency.orElse(DEFAULT_MAIL_REPOSITORY_STRONG_CONSISTENCY),
                uidReadStrongConsistency.orElse(DEFAULT_STRONG_CONSISTENCY),
                modseqReadStrongConsistency.orElse(DEFAULT_STRONG_CONSISTENCY),
                aclEnabled.orElse(true),
                uidModseqIncrement.orElse(0));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CassandraConfiguration from(Configuration propertiesConfiguration) {
        return builder()
            .aclMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_ACL, null)))
            .modSeqMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MODSEQ, null)))
            .uidMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_UID, null)))
            .flagsUpdateMessageMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MESSAGE_FLAGS_UPDATE, null)))
            .flagsUpdateMessageIdMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MESSAGE_ID_FLAGS_UPDATE, null)))
            .fetchNextPageInAdvanceRow(Optional.ofNullable(
                propertiesConfiguration.getInteger(FETCH_ADVANCE_ROW_COUNT, null)))
            .expungeChunkSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(CHUNK_SIZE_EXPUNGE, null)))
            .blobPartSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(BLOB_PART_SIZE, null)))
            .attachmentV2MigrationReadTimeout(Optional.ofNullable(
                propertiesConfiguration.getInteger(ATTACHMENT_V2_MIGRATION_READ_TIMEOUT, null)))
            .messageAttachmentIdsReadTimeout(Optional.ofNullable(
                propertiesConfiguration.getInteger(MESSAGE_ATTACHMENTID_READ_TIMEOUT, null)))
            .consistencyLevelRegular(Optional.ofNullable(
                    propertiesConfiguration.getString(CONSISTENCY_LEVEL_REGULAR)))
            .consistencyLevelLightweightTransaction(Optional.ofNullable(
                    propertiesConfiguration.getString(CONSISTENCY_LEVEL_LIGHTWEIGHT_TRANSACTION)))
            .mailboxReadRepair(Optional.ofNullable(
                propertiesConfiguration.getFloat(MAILBOX_READ_REPAIR, null)))
            .mailboxCountersReadRepairMax(Optional.ofNullable(
                propertiesConfiguration.getFloat(MAILBOX_MAX_COUNTERS_READ_REPAIR, null)))
            .mailboxCountersReadRepairChanceOneHundred(Optional.ofNullable(
                propertiesConfiguration.getFloat(MAILBOX_ONE_HUNDRED_COUNTERS_READ_REPAIR, null)))
            .mailboxReadStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MAILBOX_READ_STRONG_CONSISTENCY, null)))
            .messageReadStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MESSAGE_READ_STRONG_CONSISTENCY, null)))
            .uidReadStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(UID_READ_STRONG_CONSISTENCY, null)))
            .modseqReadStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MODSEQ_READ_STRONG_CONSISTENCY, null)))
            .messageWriteStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MESSAGE_WRITE_STRONG_CONSISTENCY, null)))
            .optimisticConsistencyLevel(Optional.ofNullable(
                propertiesConfiguration.getBoolean(OPTIMISTIC_CONSISTENCY_LEVEL, null)))
            .mailRepositoryStrongConsistency(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MAIL_REPOSITORY_STRONG_CONSISTENCY, null)))
            .aclEnabled(Optional.ofNullable(propertiesConfiguration.getBoolean(ACL_ENABLED, null)))
            .uidModseqIncrement(Optional.ofNullable(propertiesConfiguration.getInteger(UID_MODSEQ_INCREMENT, null)))
            .build();
    }

    private final int expungeChunkSize;
    private final int flagsUpdateMessageIdMaxRetry;
    private final int flagsUpdateMessageMaxRetry;
    private final int modSeqMaxRetry;
    private final int uidMaxRetry;
    private final int aclMaxRetry;
    private final int fetchNextPageInAdvanceRow;
    private final int blobPartSize;
    private final int attachmentV2MigrationReadTimeout;
    private final int messageAttachmentIdsReadTimeout;
    private final String consistencyLevelRegular;
    private final String consistencyLevelLightweightTransaction;
    private final float mailboxReadRepair;
    private final float mailboxCountersReadRepairChanceMax;
    private final float mailboxCountersReadRepairChanceOneHundred;
    private final boolean mailboxReadStrongConsistency;
    private final boolean messageReadStrongConsistency;
    private final boolean messageWriteStrongConsistency;
    private final boolean optimisticConsistencyLevel;
    private final boolean mailRepositoryStrongConsistency;
    private final boolean uidReadStrongConsistency;
    private final boolean modseqReadStrongConsistency;
    private final boolean aclEnabled;
    private final int uidModseqIncrement;

    @VisibleForTesting
    CassandraConfiguration(int aclMaxRetry, int expungeChunkSize,
                           int flagsUpdateMessageIdMaxRetry, int flagsUpdateMessageMaxRetry,
                           int modSeqMaxRetry, int uidMaxRetry, int fetchNextPageInAdvanceRow,
                           int blobPartSize, final int attachmentV2MigrationReadTimeout, int messageAttachmentIdsReadTimeout,
                           String consistencyLevelRegular, String consistencyLevelLightweightTransaction,
                           float mailboxReadRepair, float mailboxCountersReadRepairChanceMax,
                           float mailboxCountersReadRepairChanceOneHundred, boolean mailboxReadStrongConsistency,
                           boolean messageReadStrongConsistency, boolean messageWriteStrongConsistency,
                           boolean optimisticConsistencyLevel, boolean mailRepositoryStrongConsistency,
                           boolean uidReadStrongConsistency, boolean modseqReadStrongConsistency, boolean aclEnabled,
                           int uidModseqIncrement) {
        this.aclMaxRetry = aclMaxRetry;
        this.expungeChunkSize = expungeChunkSize;
        this.flagsUpdateMessageIdMaxRetry = flagsUpdateMessageIdMaxRetry;
        this.flagsUpdateMessageMaxRetry = flagsUpdateMessageMaxRetry;
        this.modSeqMaxRetry = modSeqMaxRetry;
        this.uidMaxRetry = uidMaxRetry;
        this.fetchNextPageInAdvanceRow = fetchNextPageInAdvanceRow;
        this.blobPartSize = blobPartSize;
        this.attachmentV2MigrationReadTimeout = attachmentV2MigrationReadTimeout;
        this.messageAttachmentIdsReadTimeout = messageAttachmentIdsReadTimeout;
        this.consistencyLevelRegular = consistencyLevelRegular;
        this.consistencyLevelLightweightTransaction = consistencyLevelLightweightTransaction;
        this.mailboxReadRepair = mailboxReadRepair;
        this.mailboxCountersReadRepairChanceMax = mailboxCountersReadRepairChanceMax;
        this.mailboxCountersReadRepairChanceOneHundred = mailboxCountersReadRepairChanceOneHundred;
        this.mailboxReadStrongConsistency = mailboxReadStrongConsistency;
        this.messageReadStrongConsistency = messageReadStrongConsistency;
        this.messageWriteStrongConsistency = messageWriteStrongConsistency;
        this.optimisticConsistencyLevel = optimisticConsistencyLevel;
        this.mailRepositoryStrongConsistency = mailRepositoryStrongConsistency;
        this.uidReadStrongConsistency = uidReadStrongConsistency;
        this.modseqReadStrongConsistency = modseqReadStrongConsistency;
        this.aclEnabled = aclEnabled;
        this.uidModseqIncrement = uidModseqIncrement;
    }

    public boolean isUidReadStrongConsistency() {
        return uidReadStrongConsistency;
    }

    public boolean isModseqReadStrongConsistency() {
        return modseqReadStrongConsistency;
    }

    public boolean isMailboxReadStrongConsistency() {
        return mailboxReadStrongConsistency;
    }

    public boolean isMessageWriteStrongConsistency() {
        return messageWriteStrongConsistency;
    }

    public boolean isMessageReadStrongConsistency() {
        return messageReadStrongConsistency;
    }

    public float getMailboxReadRepair() {
        return mailboxReadRepair;
    }

    public int getBlobPartSize() {
        return blobPartSize;
    }

    public int getAclMaxRetry() {
        return aclMaxRetry;
    }

    public int getExpungeChunkSize() {
        return expungeChunkSize;
    }

    public int getFlagsUpdateMessageIdMaxRetry() {
        return flagsUpdateMessageIdMaxRetry;
    }

    public int getFlagsUpdateMessageMaxRetry() {
        return flagsUpdateMessageMaxRetry;
    }

    public int getModSeqMaxRetry() {
        return modSeqMaxRetry;
    }

    public int getUidMaxRetry() {
        return uidMaxRetry;
    }

    public int getFetchNextPageInAdvanceRow() {
        return fetchNextPageInAdvanceRow;
    }

    public int getAttachmentV2MigrationReadTimeout() {
        return attachmentV2MigrationReadTimeout;
    }

    public int getMessageAttachmentIdsReadTimeout() {
        return messageAttachmentIdsReadTimeout;
    }

    public String getConsistencyLevelRegular() {
        return consistencyLevelRegular;
    }

    public String getConsistencyLevelLightweightTransaction() {
        return consistencyLevelLightweightTransaction;
    }

    public float getMailboxCountersReadRepairChanceMax() {
        return mailboxCountersReadRepairChanceMax;
    }

    public float getMailboxCountersReadRepairChanceOneHundred() {
        return mailboxCountersReadRepairChanceOneHundred;
    }

    public boolean isOptimisticConsistencyLevel() {
        return optimisticConsistencyLevel;
    }

    public boolean isMailRepositoryStrongConsistency() {
        return mailRepositoryStrongConsistency;
    }

    public boolean isAclEnabled() {
        return aclEnabled;
    }

    public int getUidModseqIncrement() {
        return uidModseqIncrement;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraConfiguration) {
            CassandraConfiguration that = (CassandraConfiguration) o;

            return Objects.equals(this.aclMaxRetry, that.aclMaxRetry)
                && Objects.equals(this.expungeChunkSize, that.expungeChunkSize)
                && Objects.equals(this.flagsUpdateMessageIdMaxRetry, that.flagsUpdateMessageIdMaxRetry)
                && Objects.equals(this.flagsUpdateMessageMaxRetry, that.flagsUpdateMessageMaxRetry)
                && Objects.equals(this.modSeqMaxRetry, that.modSeqMaxRetry)
                && Objects.equals(this.uidMaxRetry, that.uidMaxRetry)
                && Objects.equals(this.mailboxReadRepair, that.mailboxReadRepair)
                && Objects.equals(this.mailboxCountersReadRepairChanceMax, that.mailboxCountersReadRepairChanceMax)
                && Objects.equals(this.mailboxCountersReadRepairChanceOneHundred, that.mailboxCountersReadRepairChanceOneHundred)
                && Objects.equals(this.fetchNextPageInAdvanceRow, that.fetchNextPageInAdvanceRow)
                && Objects.equals(this.blobPartSize, that.blobPartSize)
                && Objects.equals(this.attachmentV2MigrationReadTimeout, that.attachmentV2MigrationReadTimeout)
                && Objects.equals(this.mailboxReadStrongConsistency, that.mailboxReadStrongConsistency)
                && Objects.equals(this.messageAttachmentIdsReadTimeout, that.messageAttachmentIdsReadTimeout)
                && Objects.equals(this.messageReadStrongConsistency, that.messageReadStrongConsistency)
                && Objects.equals(this.messageWriteStrongConsistency, that.messageWriteStrongConsistency)
                && Objects.equals(this.consistencyLevelRegular, that.consistencyLevelRegular)
                && Objects.equals(this.consistencyLevelLightweightTransaction, that.consistencyLevelLightweightTransaction)
                && Objects.equals(this.optimisticConsistencyLevel, that.optimisticConsistencyLevel)
                && Objects.equals(this.uidReadStrongConsistency, that.uidReadStrongConsistency)
                && Objects.equals(this.modseqReadStrongConsistency, that.modseqReadStrongConsistency)
                && Objects.equals(this.mailRepositoryStrongConsistency, that.mailRepositoryStrongConsistency)
                && Objects.equals(this.aclEnabled, that.aclEnabled)
                && Objects.equals(this.uidModseqIncrement, that.uidModseqIncrement);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(aclMaxRetry, expungeChunkSize, flagsUpdateMessageIdMaxRetry,
            flagsUpdateMessageMaxRetry, modSeqMaxRetry, uidMaxRetry, fetchNextPageInAdvanceRow,
            mailboxCountersReadRepairChanceOneHundred, mailboxCountersReadRepairChanceMax,
            blobPartSize, attachmentV2MigrationReadTimeout, messageAttachmentIdsReadTimeout,
            consistencyLevelRegular, consistencyLevelLightweightTransaction, mailboxReadRepair,
            messageReadStrongConsistency, mailboxReadStrongConsistency, messageWriteStrongConsistency,
            optimisticConsistencyLevel, mailRepositoryStrongConsistency, uidReadStrongConsistency,
            modseqReadStrongConsistency, aclEnabled, uidModseqIncrement);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("aclMaxRetry", aclMaxRetry)
            .add("expungeChunkSize", expungeChunkSize)
            .add("flagsUpdateMessageIdMaxRetry", flagsUpdateMessageIdMaxRetry)
            .add("flagsUpdateMessageMaxRetry", flagsUpdateMessageMaxRetry)
            .add("modSeqMaxRetry", modSeqMaxRetry)
            .add("fetchNextPageInAdvanceRow", fetchNextPageInAdvanceRow)
            .add("mailboxReadRepair", mailboxReadRepair)
            .add("mailboxCountersReadRepairChanceOneHundred", mailboxCountersReadRepairChanceOneHundred)
            .add("mailboxCountersReadRepairChanceMax", mailboxCountersReadRepairChanceMax)
            .add("uidMaxRetry", uidMaxRetry)
            .add("blobPartSize", blobPartSize)
            .add("attachmentV2MigrationReadTimeout", attachmentV2MigrationReadTimeout)
            .add("messageAttachmentIdsReadTimeout", messageAttachmentIdsReadTimeout)
            .add("messageReadStrongConsistency", messageReadStrongConsistency)
            .add("messageWriteStrongConsistency", messageWriteStrongConsistency)
            .add("mailboxReadStrongConsistency", mailboxReadStrongConsistency)
            .add("consistencyLevelRegular", consistencyLevelRegular)
            .add("consistencyLevelLightweightTransaction", consistencyLevelLightweightTransaction)
            .add("optimisticConsistencyLevel", optimisticConsistencyLevel)
            .add("mailRepositoryStrongConsistency", mailRepositoryStrongConsistency)
            .add("modseqReadStrongConsistency", modseqReadStrongConsistency)
            .add("uidReadStrongConsistency", uidReadStrongConsistency)
            .add("aclEnabled", aclEnabled)
            .add("uidModseqIncrement", uidModseqIncrement)
            .toString();
    }
}
