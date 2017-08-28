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

import java.util.Optional;

import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.FinalRecipient;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;

import com.google.common.base.Preconditions;

public class MDNReport {

    public static class Builder {
        private Optional<ReportingUserAgent> reportingUserAgentField = Optional.empty();
        private Optional<OriginalRecipient> originalRecipientField = Optional.empty();
        private Optional<FinalRecipient> finalRecipientField = Optional.empty();
        private Optional<OriginalMessageId> originalMessageIdField = Optional.empty();
        private Optional<Disposition> dispositionField = Optional.empty();

        public Builder reportingUserAgentField(ReportingUserAgent reportingUserAgentField) {
            this.reportingUserAgentField = Optional.of(reportingUserAgentField);
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

        public Builder finalRecipientField(FinalRecipient finalRecipientField) {
            this.finalRecipientField = Optional.of(finalRecipientField);
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

        public MDNReport build() {
            Preconditions.checkState(reportingUserAgentField.isPresent());
            Preconditions.checkState(finalRecipientField.isPresent());
            Preconditions.checkState(originalMessageIdField.isPresent());
            Preconditions.checkState(dispositionField.isPresent());

            return new MDNReport(reportingUserAgentField.get(),
                originalRecipientField,
                finalRecipientField.get(),
                originalMessageIdField.get(),
                dispositionField.get());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final String LINE_END = "\r\n";

    private final ReportingUserAgent reportingUserAgentField;
    private final Optional<OriginalRecipient> originalRecipientField;
    private final FinalRecipient finalRecipientField;
    private final OriginalMessageId originalMessageIdField;
    private final Disposition dispositionField;

    private MDNReport(ReportingUserAgent reportingUserAgentField, Optional<OriginalRecipient> originalRecipientField,
                     FinalRecipient finalRecipientField, OriginalMessageId originalMessageIdField, Disposition dispositionField) {
        this.reportingUserAgentField = reportingUserAgentField;
        this.originalRecipientField = originalRecipientField;
        this.finalRecipientField = finalRecipientField;
        this.originalMessageIdField = originalMessageIdField;
        this.dispositionField = dispositionField;
    }

    public ReportingUserAgent getReportingUserAgentField() {
        return reportingUserAgentField;
    }

    public Optional<OriginalRecipient> getOriginalRecipientField() {
        return originalRecipientField;
    }

    public FinalRecipient getFinalRecipientField() {
        return finalRecipientField;
    }

    public OriginalMessageId getOriginalMessageIdField() {
        return originalMessageIdField;
    }

    public Disposition getDispositionField() {
        return dispositionField;
    }

    public String formattedValue() {
        return reportingUserAgentField.formattedValue() + LINE_END
            + originalRecipientField.map(value -> value.formattedValue() + "\r\n").orElse("")
            + finalRecipientField.formattedValue() + "\r\n"
            + originalMessageIdField.formattedValue() + "\r\n"
            + dispositionField.formattedValue() + "\r\n";

    }
}
