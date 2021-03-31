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

package org.apache.james.mdn;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.Error;
import org.apache.james.mdn.fields.ExtensionField;
import org.apache.james.mdn.fields.Field;
import org.apache.james.mdn.fields.FinalRecipient;
import org.apache.james.mdn.fields.Gateway;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.fields.Text;
import org.apache.james.util.StreamUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MDNReport {

    public static final String LINE_END = "\r\n";
    public static final String EXTENSION_DELIMITER = LINE_END;

    public static class Builder {
        private Optional<ReportingUserAgent> reportingUserAgentField = Optional.empty();
        private Optional<Gateway> gatewayField = Optional.empty();
        private Optional<OriginalRecipient> originalRecipientField = Optional.empty();
        private Optional<FinalRecipient> finalRecipientField = Optional.empty();
        private Optional<OriginalMessageId> originalMessageIdField = Optional.empty();
        private Optional<Disposition> dispositionField = Optional.empty();
        private ImmutableList.Builder<Error> errorField = ImmutableList.builder();
        private ImmutableList.Builder<ExtensionField> extensionFields = ImmutableList.builder();

        public Builder reportingUserAgentField(ReportingUserAgent reportingUserAgentField) {
            this.reportingUserAgentField = Optional.of(reportingUserAgentField);
            return this;
        }

        public Builder originalRecipientField(String originalRecipient) {
            this.originalRecipientField = Optional.ofNullable(originalRecipient)
                .map(Text::fromRawText)
                .map(text -> OriginalRecipient.builder().originalRecipient(text).build());
            return this;
        }

        public Builder originalRecipientField(OriginalRecipient originalRecipientField) {
            this.originalRecipientField = Optional.of(originalRecipientField);
            return this;
        }

        public Builder originalRecipientField(Optional<OriginalRecipient> originalRecipientField) {
            this.originalRecipientField = originalRecipientField;
            return this;
        }

        public Builder gatewayField(Gateway gatewayField) {
            this.gatewayField = Optional.of(gatewayField);
            return this;
        }

        public Builder addErrorField(String message) {
            this.errorField.add(new Error(Text.fromRawText(message)));
            return this;
        }


        public Builder addErrorField(Error errorField) {
            this.errorField.add(errorField);
            return this;
        }

        public Builder addErrorFields(Error... errorField) {
            this.errorField.add(errorField);
            return this;
        }

        public Builder finalRecipientField(String finalRecipientField) {
            this.finalRecipientField = Optional.of(FinalRecipient.builder().finalRecipient(Text.fromRawText(finalRecipientField)).build());
            return this;
        }

        public Builder finalRecipientField(FinalRecipient finalRecipientField) {
            this.finalRecipientField = Optional.of(finalRecipientField);
            return this;
        }

        public Builder originalMessageIdField(String originalMessageIdField) {
            this.originalMessageIdField = Optional.of(new OriginalMessageId(originalMessageIdField));
            return this;
        }

        public Builder originalMessageIdField(OriginalMessageId originalMessageIdField) {
            this.originalMessageIdField = Optional.of(originalMessageIdField);
            return this;
        }

        public Builder dispositionField(Disposition dispositionField) {
            this.dispositionField = Optional.of(dispositionField);
            return this;
        }

        public Builder withExtensionField(ExtensionField extensionField) {
            this.extensionFields.add(extensionField);
            return this;
        }

        public Builder withExtensionFields(ExtensionField... extensionField) {
            this.extensionFields.add(extensionField);
            return this;
        }

        public MDNReport build() {
            Preconditions.checkState(finalRecipientField.isPresent());
            Preconditions.checkState(dispositionField.isPresent());

            return new MDNReport(reportingUserAgentField,
                gatewayField, originalRecipientField,
                finalRecipientField.get(),
                originalMessageIdField,
                dispositionField.get(),
                errorField.build(),
                extensionFields.build());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<ReportingUserAgent> reportingUserAgentField;
    private final Optional<Gateway> gatewayField;
    private final Optional<OriginalRecipient> originalRecipientField;
    private final FinalRecipient finalRecipientField;
    private final Optional<OriginalMessageId> originalMessageIdField;
    private final Disposition dispositionField;
    private final ImmutableList<Error> errorFields;
    private final ImmutableList<ExtensionField> extensionFields;

    @VisibleForTesting
    MDNReport(Optional<ReportingUserAgent> reportingUserAgentField, Optional<Gateway> gatewayField, Optional<OriginalRecipient> originalRecipientField,
                      FinalRecipient finalRecipientField, Optional<OriginalMessageId> originalMessageIdField, Disposition dispositionField,
                      ImmutableList<Error> errorFields, ImmutableList<ExtensionField> extensionFields) {
        this.reportingUserAgentField = reportingUserAgentField;
        this.gatewayField = gatewayField;
        this.originalRecipientField = originalRecipientField;
        this.finalRecipientField = finalRecipientField;
        this.originalMessageIdField = originalMessageIdField;
        this.dispositionField = dispositionField;
        this.errorFields = errorFields;
        this.extensionFields = extensionFields;
    }

    public Optional<ReportingUserAgent> getReportingUserAgentField() {
        return reportingUserAgentField;
    }

    public Optional<OriginalRecipient> getOriginalRecipientField() {
        return originalRecipientField;
    }

    public FinalRecipient getFinalRecipientField() {
        return finalRecipientField;
    }

    public Optional<OriginalMessageId> getOriginalMessageIdField() {
        return originalMessageIdField;
    }

    public Disposition getDispositionField() {
        return dispositionField;
    }

    public Optional<Gateway> getGatewayField() {
        return gatewayField;
    }

    public List<Error> getErrorFields() {
        return errorFields;
    }

    public ImmutableList<ExtensionField> getExtensionFields() {
        return extensionFields;
    }

    public String formattedValue() {
        Stream<Optional<? extends Field>> definedFields =
            Stream.of(
                reportingUserAgentField,
                gatewayField,
                originalRecipientField,
                Optional.of(finalRecipientField),
                originalMessageIdField,
                Optional.of(dispositionField));
        Stream<Optional<? extends Field>> errors =
            errorFields.stream().map(Optional::of);
        Stream<Optional<? extends Field>> extentions =
            extensionFields.stream().map(Optional::of);

        return StreamUtils.flatten(
            Stream.of(
                definedFields,
                errors,
                extentions))
            .flatMap(Optional::stream)
            .map(Field::formattedValue)
            .collect(Collectors.joining(LINE_END)) + LINE_END;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MDNReport) {
            MDNReport that = (MDNReport) o;

            return Objects.equals(this.reportingUserAgentField, that.reportingUserAgentField)
                && Objects.equals(this.dispositionField, that.dispositionField)
                && Objects.equals(this.errorFields, that.errorFields)
                && Objects.equals(this.finalRecipientField, that.finalRecipientField)
                && Objects.equals(this.gatewayField, that.gatewayField)
                && Objects.equals(this.originalMessageIdField, that.originalMessageIdField)
                && Objects.equals(this.extensionFields, that.extensionFields)
                && Objects.equals(this.originalRecipientField, that.originalRecipientField);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(reportingUserAgentField, gatewayField, originalMessageIdField, originalRecipientField,
            dispositionField, errorFields, extensionFields, finalRecipientField);
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
