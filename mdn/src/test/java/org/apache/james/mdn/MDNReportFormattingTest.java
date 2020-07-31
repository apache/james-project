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

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.AddressType;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.Error;
import org.apache.james.mdn.fields.ExtensionField;
import org.apache.james.mdn.fields.FinalRecipient;
import org.apache.james.mdn.fields.Gateway;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.fields.Text;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.jupiter.api.Test;

class MDNReportFormattingTest {

    @Test
    void generateMDNReportShouldFormatAutomaticActions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatManualActions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatTypeDispatcher() {
        Disposition disposition = Disposition.builder()
        .actionMode(DispositionActionMode.Manual)
        .sendingMode(DispositionSendingMode.Manual)
        .type(DispositionType.Dispatched)
        .addModifier(DispositionModifier.Error)
        .addModifier(DispositionModifier.Failed)
        .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;dispatched/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatTypeDisplayed() {
        Disposition disposition = Disposition.builder()
        .actionMode(DispositionActionMode.Manual)
        .sendingMode(DispositionSendingMode.Manual)
        .type(DispositionType.Displayed)
        .addModifier(DispositionModifier.Error)
        .addModifier(DispositionModifier.Failed)
        .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;displayed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatTypeDeleted() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatAllModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifiers(DispositionModifier.Error, DispositionModifier.Expired, DispositionModifier.Failed,
                DispositionModifier.MailboxTerminated, DispositionModifier.Superseded, DispositionModifier.Warning)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,expired,failed,mailbox-terminated,superseded,warning\r\n");
    }

    @Test
    void generateMDNReportShouldFormatOneModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error\r\n");
    }

    @Test
    void generateMDNReportShouldFormatUnknownModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .addModifier(new DispositionModifier("new"))
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/new\r\n");
    }

    @Test
    void generateMDNReportShouldFormatNoModifier() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted\r\n");
    }

    @Test
    void generateMDNReportShouldFormatNullUserAgentProduct() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; \r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatNullOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatWhenMissingOriginalMessageId() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatGateway() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .gatewayField(Gateway.builder().name(Text.fromRawText("host.com")).build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "MDN-Gateway: dns;host.com\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatGatewayWithExoticNameType() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .gatewayField(Gateway.builder().nameType(new AddressType("postal")).name(Text.fromRawText("5 rue Charles mercier")).build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "MDN-Gateway: postal;5 rue Charles mercier\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatExoticAddressTypeForOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().addressType(new AddressType("roomNumber")).originalRecipient(Text.fromRawText("385")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: roomNumber; 385\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatMultilineAddresses() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .gatewayField(Gateway.builder().nameType(new AddressType("postal")).name(Text.fromRawText("8 rue Charles mercier\n 36555 Saint Coincoin\n France")).build())
            .finalRecipientField(FinalRecipient.builder().addressType(new AddressType("postal")).finalRecipient(Text.fromRawText("5 rue Mercier\n 36555 Saint Coincoin\n France")).build())
            .originalRecipientField(OriginalRecipient.builder().addressType(new AddressType("postal")).originalRecipient(Text.fromRawText("3 rue Mercier\n 36555 Saint Coincoin\n France")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "MDN-Gateway: postal;8 rue Charles mercier\r\n" +
                " 36555 Saint Coincoin\r\n" +
                " France\r\n" +
                "Original-Recipient: postal; 3 rue Mercier\r\n" +
                " 36555 Saint Coincoin\r\n" +
                " France\r\n" +
                "Final-Recipient: postal; 5 rue Mercier\r\n" +
                " 36555 Saint Coincoin\r\n" +
                " France\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatUnknownAddressTypeForOriginalRecipient() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.ofUnknown(Text.fromRawText("#$%*")))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: unknown; #$%*\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatExoticFinalRecipientAddressType() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().addressType(new AddressType("roomNumber")).finalRecipient(Text.fromRawText("781")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: roomNumber; 781\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    void generateMDNReportShouldFormatErrorField() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .addErrorField(new Error(Text.fromRawText("An error message")))
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "Error: An error message\r\n");
    }

    @Test
    void generateMDNReportShouldFormatErrorFields() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .addErrorFields(
                new Error(Text.fromRawText("An error message")),
                new Error(Text.fromRawText("A second error message")))
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "Error: An error message\r\n" +
                "Error: A second error message\r\n");
    }

    @Test
    void generateMDNReportShouldFormatErrorFieldsOnSeveralLines() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .addErrorField(new Error(Text.fromRawText("An error message\non several lines")))
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "Error: An error message\r\n" +
                " on several lines\r\n");
    }

    @Test
    void generateMDNReportShouldFormatOneExtension() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
         .withExtensionField(ExtensionField.builder().fieldName("X-OPENPAAS-IP").rawValue("177.177.177.77").build())
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "X-OPENPAAS-IP: 177.177.177.77\r\n");
    }


    @Test
    void generateMDNReportShouldFormatManyExtensions() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .addModifier(DispositionModifier.Error)
            .addModifier(DispositionModifier.Failed)
            .build();

        String report = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build())
            .finalRecipientField(FinalRecipient.builder().finalRecipient(Text.fromRawText("final_recipient")).build())
            .originalRecipientField(OriginalRecipient.builder().originalRecipient(Text.fromRawText("originalRecipient")).build())
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .withExtensionFields(
                ExtensionField.builder().fieldName("X-OPENPAAS-IP").rawValue("177.177.177.77").build(),
                ExtensionField.builder().fieldName("X-OPENPAAS-PORT").rawValue("8000").build())
            .build()
            .formattedValue();

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                "X-OPENPAAS-IP: 177.177.177.77\r\n" +
                "X-OPENPAAS-PORT: 8000\r\n");
    }
}
