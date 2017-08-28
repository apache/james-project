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
        Disposition disposition = new Disposition(DispositionActionMode.Automatic, DispositionSendingMode.Automatic, DispositionType.Processed);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatManualActions() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Processed);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;processed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDenied() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Denied);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;denied/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDispatcher() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Dispatched);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;dispatched/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDisplayed() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Displayed);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;displayed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeFailed() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Failed);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;failed/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatTypeDeleted() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatAllModifier() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Expired, DispositionModifier.Failed,
            DispositionModifier.MailboxTerminated, DispositionModifier.Superseded, DispositionModifier.Warning};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted/error,expired,failed,mailbox-terminated,superseded,warning\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNoModifier() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;deleted\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNoModifierNullType() {
        DispositionType type = null;
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Manual, type);
        DispositionModifier[] dispostionModifiers = {};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-manually;\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullActionMode() {
        DispositionActionMode actionMode = null;
        Disposition disposition = new Disposition(actionMode, DispositionSendingMode.Manual, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: /MDN-sent-manually;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullSendingMode() {
        DispositionSendingMode sendingMode = null;
        Disposition disposition = new Disposition(DispositionActionMode.Manual, sendingMode, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullUserAgentName() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Automatic, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String reporting_ua_name = null;
        String report = MDNFactory.generateMDNReport(reporting_ua_name, "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: ; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullUserAgentProduct() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Automatic, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String reporting_ua_product = null;
        String report = MDNFactory.generateMDNReport("UA_name", reporting_ua_product, "originalRecipient",
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; \r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullOriginalRecipient() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Automatic, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String original_recipient = null;
        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", original_recipient,
            "final_recipient", "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullFinalRecipient() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Automatic, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String final_recipient = null;
        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            final_recipient, "original_message_id", disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; \r\n" +
                "Original-Message-ID: original_message_id\r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportShouldFormatNullOriginalMessageId() {
        Disposition disposition = new Disposition(DispositionActionMode.Manual, DispositionSendingMode.Automatic, DispositionType.Deleted);
        DispositionModifier[] dispostionModifiers = {DispositionModifier.Error, DispositionModifier.Failed};
        disposition.setDispositionModifiers(dispostionModifiers);

        String original_message_id = null;
        String report = MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", original_message_id, disposition);

        assertThat(report)
            .isEqualTo("Reporting-UA: UA_name; UA_product\r\n" +
                "Original-Recipient: rfc822; originalRecipient\r\n" +
                "Final-Recepient: rfc822; final_recipient\r\n" +
                "Original-Message-ID: \r\n" +
                "Disposition: manual-action/MDN-sent-automatically;deleted/error,failed\r\n");
    }

    @Test
    public void generateMDNReportThrowOnNullDisposition() {
        Disposition disposition = null;
        expectedException.expect(NullPointerException.class);
        MDNFactory.generateMDNReport("UA_name", "UA_product", "originalRecipient",
            "final_recipient", "original_message_id", disposition);
    }
}
