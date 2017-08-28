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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.Error;
import org.apache.james.mdn.fields.FinalRecipient;
import org.apache.james.mdn.fields.Gateway;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.fields.Text;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MDNFactoryTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void generateMDNReportShouldFormatAutomaticActions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatManualActions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDispatcher() {
        Disposition disposition = Disposition.builder()
        .actionMode(DispositionActionMode.Manual)
        .sendingMode(DispositionSendingMode.Manual)
        .type(DispositionType.Dispatched)
        .addModifier(DispositionModifier.Error)
        .addModifier(DispositionModifier.Failed)
        .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;dispatched/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDisplayed() {
        Disposition disposition = Disposition.builder()
        .actionMode(DispositionActionMode.Manual)
        .sendingMode(DispositionSendingMode.Manual)
        .type(DispositionType.Displayed)
        .addModifier(DispositionModifier.Error)
        .addModifier(DispositionModifier.Failed)
        .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;displayed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDeleted() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatAllModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifiers(DispositionModifier.Error, DispositionModifier.Expired, DispositionModifier.Failed,
                DispositionModifier.MailboxTerminated, DispositionModifier.Superseded, DispositionModifier.Warning)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,expired,failed,mailbox-terminated,superseded,warning\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatOneModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatUnknownModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(new DispositionModifier("new"))
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/new\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNoModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullUserAgentProduct() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.empty()))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; \r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatWhenMissingOriginalMessageId() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportThrowOnNullDisposition() {
        expectedException.expect(IllegalStateException.class);

        MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .build();
    }

    @Test
    public void generateMDNReportShouldFormatGateway() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .gatewayField(new Gateway("host.com"))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "MDN-Gateway: dns;host.com\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatGatewayWithExoticNameType() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .gatewayField(new Gateway("postal", "5 rue Charles mercier"))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "MDN-Gateway: postal;5 rue Charles mercier\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatExoticAddressTypeForOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("roomNumber", "385"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: roomNumber; 385\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatUnknownAddressTypeForOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(OriginalRecipient.ofUnknown("#$%*"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: unknown; #$%*\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatExoticFinalRecipientAddressType() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("roomNumber", "781"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: roomNumber; 781\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldThrowWhenMissingFinalField() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        expectedException.expect(IllegalStateException.class);

        MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build();
    }

    @Test
    public void generateMDNReportShouldFormatErrorFields() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .errorField(new Error(Text.fromRawText("An error message")))
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "Error: An error message\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatErrorFieldsOnSeveralLines() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                Optional.of("UA_product")))
            .finalRecipientField(new FinalRecipient("final_recipient"))
            .originalRecipientField(new OriginalRecipient("originalRecipient"))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .errorField(new Error(Text.fromRawText("An error message\non several lines")))
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "Error: An error message\r\n" +
                " on several lines\r\n");
    }
}
