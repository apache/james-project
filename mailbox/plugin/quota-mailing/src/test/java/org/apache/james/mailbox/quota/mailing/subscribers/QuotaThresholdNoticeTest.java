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

package org.apache.james.mailbox.quota.mailing.subscribers;

import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.DEFAULT_CONFIGURATION;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._95;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration.RenderingInformation;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaThresholdNoticeTest {

    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() {
        fileSystem = FileSystemImpl.forTesting();
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThresholdNotice.class)
            .verify();
    }

    @Test
    void buildShouldReturnEmptyWhenNoThresholds() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .withConfiguration(DEFAULT_CONFIGURATION)
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenNoChanges() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.noChanges())
            .withConfiguration(DEFAULT_CONFIGURATION)
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenBelow() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_80, NOW)))
            .withConfiguration(DEFAULT_CONFIGURATION)
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenAboveButRecentChanges() {
        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), AlreadyReachedDuringGracePeriod))
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnPresentWhenAbove() {
        Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCountLimit, QuotaCountUsage> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.empty(), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota, DEFAULT_CONFIGURATION));
    }

    @Test
    void buildShouldFilterOutNotInterestingFields() {
        Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCountLimit, QuotaCountUsage> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.lowerThresholdReached(countThresholdChange))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.empty(), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota, DEFAULT_CONFIGURATION));
    }

    @Test
    void buildShouldKeepAllInterestingFields() {
        Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCountLimit, QuotaCountUsage> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.of(countThresholdChange.getQuotaThreshold()), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota, DEFAULT_CONFIGURATION));
    }

    @Test
    void generateReportShouldGenerateAHumanReadableMessage() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._92_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes on a total of 100 bytes allocated to you.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages on a total of 100 allowed for you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldGenerateAHumanReadableMessageWhenNoCountQuota() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                .used(QuotaCountUsage.count(92))
                .computedLimit(QuotaCountLimit.unlimited())
                .build())
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes on a total of 100 bytes allocated to you.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldGenerateAHumanReadableMessageWhenNoSizeQuota() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .countQuota(Counts._92_PERCENT)
            .sizeQuota(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                .used(QuotaSizeUsage.size(82))
                .computedLimit(QuotaSizeLimit.unlimited())
                .build())
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages on a total of 100 allowed for you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldOmitCountPartWhenNone() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._72_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes on a total of 100 bytes allocated to you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldFormatSizeUnits() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                .used(QuotaSizeUsage.size(801 * 1024 * 1024))
                .computedLimit(QuotaSizeLimit.size(1 * 1024 * 1024 * 1024))
                .build())
            .countQuota(Counts._72_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .contains("You currently occupy 801 MiB on a total of 1 GiB allocated to you.");
    }

    @Test
    void generateReportShouldTruncateLowDigitsFormatSizeUnits() throws Exception {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                .used(QuotaSizeUsage.size(801 * 1024 * 1024))
                .computedLimit(QuotaSizeLimit.size((2L * 1024 * 1024 * 1024) - 1))
                .build())
            .countQuota(Counts._72_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .contains("You currently occupy 801 MiB on a total of 1.99 GiB allocated to you.");
    }

    @Test
    void generateReportShouldOmitSizePartWhenNone() throws Exception {
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._92_PERCENT)
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages on a total of 100 allowed for you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldNotFailWhenUnlimitedQuotaExceedsAThreshold() throws Exception {
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .withConfiguration(DEFAULT_CONFIGURATION)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport(fileSystem))
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void renderingShouldUsePerThresholdTemplate() throws Exception {
        QuotaMailingListenerConfiguration configuration = QuotaMailingListenerConfiguration.builder()
            .addThreshold(_80, RenderingInformation.from(
                "classpath://templates/body1.mustache",
                "classpath://templates/subject1.mustache"))
            .addThreshold(_95, RenderingInformation.from(
                "classpath://templates/body2.mustache",
                "classpath://templates/subject2.mustache"))
            .build();

        QuotaThresholdNotice quotaThresholdNotice1 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        QuotaThresholdNotice quotaThresholdNotice2 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .sizeQuota(Sizes._992_PERTHOUSAND)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_95, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaThresholdNotice1.generateSubject(fileSystem))
            .isEqualTo("[SUBJECT_1]");
        softly.assertThat(quotaThresholdNotice1.generateReport(fileSystem))
            .isEqualTo("[BODY_1]");
        softly.assertThat(quotaThresholdNotice2.generateSubject(fileSystem))
            .isEqualTo("[SUBJECT_2]");
        softly.assertThat(quotaThresholdNotice2.generateReport(fileSystem))
            .isEqualTo("[BODY_2]");
        softly.assertAll();
    }

    @Test
    void renderingShouldUseMostSignificantThreshold() throws Exception {
        QuotaMailingListenerConfiguration configuration = QuotaMailingListenerConfiguration.builder()
            .addThreshold(_80, RenderingInformation.from(
                "classpath://templates/body1.mustache",
                "classpath://templates/subject1.mustache"))
            .addThreshold(_95, RenderingInformation.from(
                "classpath://templates/body2.mustache",
                "classpath://templates/subject2.mustache"))
            .build();

        QuotaThresholdNotice quotaThresholdNotice1 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .countQuota(Counts._85_PERCENT)
            .sizeQuota(Sizes._992_PERTHOUSAND)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), NotAlreadyReachedDuringGracePeriod))
            .sizeThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_95, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaThresholdNotice1.generateSubject(fileSystem))
            .isEqualTo("[SUBJECT_2]");
        softly.assertThat(quotaThresholdNotice1.generateReport(fileSystem))
            .isEqualTo("[BODY_2]");
        softly.assertAll();
    }

    @Test
    void renderingShouldDefaultToGlobalValueWhenSpecificThresholdValueIsOmmited() throws Exception {
        QuotaMailingListenerConfiguration configuration = QuotaMailingListenerConfiguration.builder()
            .addThreshold(_80, RenderingInformation.from(
                Optional.empty(),
                Optional.of("classpath://templates/subject1.mustache")))
            .addThreshold(_95, RenderingInformation.from(
                Optional.of("classpath://templates/body1.mustache"),
                Optional.empty()))
            .subjectTemplate("classpath://templates/subject2.mustache")
            .bodyTemplate("classpath://templates/body2.mustache")
            .build();

        QuotaThresholdNotice quotaThresholdNotice1 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        QuotaThresholdNotice quotaThresholdNotice2 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .sizeQuota(Sizes._992_PERTHOUSAND)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_95, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaThresholdNotice1.generateSubject(fileSystem))
            .isEqualTo("[SUBJECT_1]");
        softly.assertThat(quotaThresholdNotice1.generateReport(fileSystem))
            .isEqualTo("[BODY_2]");
        softly.assertThat(quotaThresholdNotice2.generateSubject(fileSystem))
            .isEqualTo("[SUBJECT_2]");
        softly.assertThat(quotaThresholdNotice2.generateReport(fileSystem))
            .isEqualTo("[BODY_1]");
        softly.assertAll();
    }

    @Test
    void renderingShouldDefaultToDefaultValueWhenSpecificThresholdAndGlobalValueIsOmited() throws Exception {
        QuotaMailingListenerConfiguration configuration = QuotaMailingListenerConfiguration.builder()
            .addThreshold(_80, RenderingInformation.from(
                Optional.of("classpath://templates/body2.mustache"),
                Optional.empty()))
            .build();

        QuotaThresholdNotice quotaThresholdNotice1 = QuotaThresholdNotice.builder()
            .withConfiguration(configuration)
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._UNLIMITED)
            .countThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), NotAlreadyReachedDuringGracePeriod))
            .build()
            .get();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaThresholdNotice1.generateSubject(fileSystem))
            .isEqualTo("Warning: Your email usage just exceeded a configured threshold");
        softly.assertThat(quotaThresholdNotice1.generateReport(fileSystem))
            .isEqualTo("[BODY_2]");
        softly.assertAll();
    }
}