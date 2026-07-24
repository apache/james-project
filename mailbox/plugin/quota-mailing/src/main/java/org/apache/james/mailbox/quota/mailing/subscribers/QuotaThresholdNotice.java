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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.util.SizeFormat;

import com.github.fge.lambdas.Throwing;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class QuotaThresholdNotice {
    private static final String ALTERNATIVE_SUB_TYPE = "alternative";
    private static final String TEXT_HTML_UTF8_TYPE = "text/html; charset=UTF-8";

    public static class Builder {
        private Optional<QuotaThreshold> countThreshold;
        private Optional<QuotaThreshold> sizeThreshold;
        private Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota;
        private Quota<QuotaCountLimit, QuotaCountUsage> countQuota;
        private QuotaMailingListenerConfiguration configuration;

        public Builder() {
            countThreshold = Optional.empty();
            sizeThreshold = Optional.empty();
        }

        public Builder sizeQuota(Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota) {
            this.sizeQuota = sizeQuota;
            return this;
        }

        public Builder countQuota(Quota<QuotaCountLimit, QuotaCountUsage> countQuota) {
            this.countQuota = countQuota;
            return this;
        }

        public Builder countThreshold(HistoryEvolution countHistoryEvolution) {
            this.countThreshold = Optional.of(countHistoryEvolution)
                .filter(this::needsNotification)
                .flatMap(HistoryEvolution::getThresholdChange)
                .map(QuotaThresholdChange::getQuotaThreshold);
            return this;
        }

        public Builder sizeThreshold(HistoryEvolution sizeHistoryEvolution) {
            this.sizeThreshold = Optional.of(sizeHistoryEvolution)
                .filter(this::needsNotification)
                .flatMap(HistoryEvolution::getThresholdChange)
                .map(QuotaThresholdChange::getQuotaThreshold);
            return this;
        }

        public Builder withConfiguration(QuotaMailingListenerConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        boolean needsNotification(HistoryEvolution evolution) {
            return evolution.getThresholdHistoryChange() == HistoryEvolution.HistoryChangeType.HigherThresholdReached
                && evolution.currentThresholdNotRecentlyReached();
        }

        public Optional<QuotaThresholdNotice> build() {
            Preconditions.checkNotNull(configuration);
            Preconditions.checkNotNull(sizeQuota);
            Preconditions.checkNotNull(configuration);

            if (sizeThreshold.isPresent() || countThreshold.isPresent()) {
                return Optional.of(
                    new QuotaThresholdNotice(countThreshold, sizeThreshold, sizeQuota, countQuota, configuration));
            }
            return Optional.empty();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<QuotaThreshold> countThreshold;
    private final Optional<QuotaThreshold> sizeThreshold;
    private final Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota;
    private final Quota<QuotaCountLimit, QuotaCountUsage> countQuota;
    private final QuotaMailingListenerConfiguration configuration;

    @VisibleForTesting
    QuotaThresholdNotice(Optional<QuotaThreshold> countThreshold, Optional<QuotaThreshold> sizeThreshold,
                         Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota, Quota<QuotaCountLimit, QuotaCountUsage> countQuota, QuotaMailingListenerConfiguration configuration) {
        this.countThreshold = countThreshold;
        this.sizeThreshold = sizeThreshold;
        this.sizeQuota = sizeQuota;
        this.countQuota = countQuota;
        this.configuration = configuration;
    }

    public MimeMessageBuilder generateMimeMessage(FileSystem fileSystem) throws IOException, MessagingException {
        MimeMessageBuilder mimeMessageBuilder = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject(generateSubject(fileSystem));

        Optional<String> htmlReport = generateHtmlReport(fileSystem);
        if (htmlReport.isEmpty()) {
            return mimeMessageBuilder.setText(generateReport(fileSystem));
        }
        return mimeMessageBuilder.setContent(MimeMessageBuilder.multipartBuilder()
            .subType(ALTERNATIVE_SUB_TYPE)
            .addBody(MimeMessageBuilder.bodyPartBuilder()
                .data(generateReport(fileSystem))
                .type(MimeMessageBuilder.DEFAULT_TEXT_PLAIN_UTF8_TYPE))
            .addBody(MimeMessageBuilder.bodyPartBuilder()
                .data(htmlReport.get())
                .type(TEXT_HTML_UTF8_TYPE)));
    }

    @VisibleForTesting
    String generateSubject(FileSystem fileSystem) throws IOException {
        return renderTemplate(fileSystem,
            configuration.getSubjectTemplate(mostSignificantThreshold()));
    }

    @VisibleForTesting
    String generateReport(FileSystem fileSystem) throws IOException {
        return renderTemplate(fileSystem,
            configuration.getBodyTemplate(mostSignificantThreshold()));
    }

    @VisibleForTesting
    Optional<String> generateHtmlReport(FileSystem fileSystem) throws IOException {
        return configuration.getHtmlBodyTemplate(mostSignificantThreshold())
            .map(Throwing.function((String template) -> renderTemplate(fileSystem, template)).sneakyThrow());
    }

    private QuotaThreshold mostSignificantThreshold() {
        return Stream.of(countThreshold, sizeThreshold)
            .flatMap(Optional::stream)
            .min(Comparator.reverseOrder())
            .get();
    }

    private String renderTemplate(FileSystem fileSystem, String template) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8)) {

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(getPatternReader(fileSystem, template), "example");
            mustache.execute(writer, computeScopes());
            writer.flush();
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private StringReader getPatternReader(FileSystem fileSystem, String path) throws IOException {
        try (InputStream patternStream = fileSystem.getResource(path);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            IOUtils.copy(patternStream, byteArrayOutputStream);
            String pattern = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            return new StringReader(pattern);
        }
    }

    private HashMap<String, Object> computeScopes() {
        HashMap<String, Object> scopes = new HashMap<>();

        scopes.put("hasExceededSizeThreshold", sizeThreshold.isPresent());
        scopes.put("hasExceededCountThreshold", countThreshold.isPresent());
        sizeThreshold.ifPresent(value -> scopes.put("sizeThreshold", value.getQuotaOccupationRatioAsPercent()));
        countThreshold.ifPresent(value -> scopes.put("countThreshold", value.getQuotaOccupationRatioAsPercent()));

        scopes.put("usedSize", SizeFormat.format(sizeQuota.getUsed().asLong()));
        scopes.put("hasSizeLimit", sizeQuota.getLimit().isLimited());
        if (sizeQuota.getLimit().isLimited()) {
            scopes.put("limitSize", SizeFormat.format(sizeQuota.getLimit().asLong()));
        }

        scopes.put("usedCount", countQuota.getUsed().asLong());
        scopes.put("hasCountLimit", countQuota.getLimit().isLimited());
        if (countQuota.getLimit().isLimited()) {
            scopes.put("limitCount", countQuota.getLimit().asLong());
        }

        return scopes;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholdNotice) {
            QuotaThresholdNotice that = (QuotaThresholdNotice) o;

            return Objects.equals(this.countThreshold, that.countThreshold)
                && Objects.equals(this.sizeThreshold, that.sizeThreshold)
                && Objects.equals(this.sizeQuota, that.sizeQuota)
                && Objects.equals(this.countQuota, that.countQuota)
                && Objects.equals(this.configuration, that.configuration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(countThreshold, sizeThreshold, sizeQuota, countQuota, configuration);
    }

}
