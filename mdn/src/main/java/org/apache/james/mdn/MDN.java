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

import java.util.Objects;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;

public class MDN {
    public static class Builder {
        private String humanReadableText;
        private MDNReport report;

        public Builder report(MDNReport report) {
            Preconditions.checkNotNull(report);
            this.report = report;
            return this;
        }

        public Builder humanReadableText(String humanReadableText) {
            Preconditions.checkNotNull(humanReadableText);
            this.humanReadableText = humanReadableText;
            return this;
        }

        public MDN build() {
            Preconditions.checkState(report != null);
            Preconditions.checkState(humanReadableText != null);
            return new MDN(humanReadableText, report);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String humanReadableText;
    private final MDNReport report;

    public MDN(String humanReadableText, MDNReport report) {
        this.humanReadableText = humanReadableText;
        this.report = report;
    }

    public String getHumanReadableText() {
        return humanReadableText;
    }

    public MDNReport getReport() {
        return report;
    }

    public MimeMultipart asMultipart() throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType("report");
        multipart.addBodyPart(computeHumanReadablePart());
        multipart.addBodyPart(computeReportPart());
        // The optional third part, the original message is omitted.
        // We don't want to propogate over-sized, virus infected or
        // other undesirable mail!
        // There is the option of adding a Text/RFC822-Headers part, which
        // includes only the RFC 822 headers of the failed message. This is
        // described in RFC 1892. It would be a useful addition!
        return multipart;
    }

    public MimeMessage asMimeMessage() throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setContent(asMultipart());
        return mimeMessage;
    }

    public BodyPart computeHumanReadablePart() throws MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(humanReadableText, Charsets.UTF_8.displayName());
        textPart.setDisposition(MimeMessage.INLINE);
        return textPart;
    }

    public BodyPart computeReportPart() throws MessagingException {
        MimeBodyPart mdnPart = new MimeBodyPart();
        mdnPart.setContent(report.formattedValue(), "message/disposition-notification");
        return mdnPart;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MDN) {
            MDN mdn = (MDN) o;

            return Objects.equals(this.humanReadableText, mdn.humanReadableText)
                && Objects.equals(this.report, mdn.report);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(humanReadableText, report);
    }
}
