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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MDNReportTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContact() {
        EqualsVerifier.forClass(MDNReport.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void generateMDNReportThrowOnNullDisposition() {
        expectedException.expect(IllegalStateException.class);

        MDNReport.builder()
            .reportingUserAgentField(new ReportingUserAgent(
                "UA_name",
                "UA_product"))
            .finalRecipientField(new FinalRecipient(Text.fromRawText("final_recipient")))
            .originalRecipientField(new OriginalRecipient(Text.fromRawText("originalRecipient")))
            .build();
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
                "UA_product"))
            .originalRecipientField(new OriginalRecipient(Text.fromRawText("originalRecipient")))
            .originalMessageIdField(new OriginalMessageId("original_message_id"))
            .dispositionField(disposition)
            .build();
    }

    @Test
    public void shouldBuildWithMinimalSubset() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();

        FinalRecipient finalRecipientField = new FinalRecipient(Text.fromRawText("any@domain.com"));
        MDNReport mdnReport = MDNReport.builder()
            .finalRecipientField(finalRecipientField)
            .dispositionField(disposition)
            .build();

        assertThat(mdnReport)
            .isEqualTo(new MDNReport(
                Optional.empty(), Optional.empty(), Optional.empty(), finalRecipientField, Optional.empty(), disposition,
                ImmutableList.of(), ImmutableList.of()));
    }

    @Test
    public void shouldBuildWithMaximalSubset() {
        Disposition disposition = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();
        FinalRecipient finalRecipientField = new FinalRecipient(Text.fromRawText("any@domain.com"));
        ExtensionField extensionField1 = new ExtensionField("name1", "value1");
        ExtensionField extensionField2 = new ExtensionField("name2", "value2");
        Gateway gateway = new Gateway(Text.fromRawText("address"));
        OriginalMessageId originalMessageIdField = new OriginalMessageId("msgId");
        OriginalRecipient originalRecipientField = new OriginalRecipient(Text.fromRawText("address"));
        ReportingUserAgent reportingUserAgentField = new ReportingUserAgent("name");
        Error errorField1 = new Error(Text.fromRawText("error 1"));
        Error errorField2 = new Error(Text.fromRawText("error 2"));

        MDNReport mdnReport = MDNReport.builder()
            .withExtensionField(extensionField1)
            .withExtensionField(extensionField2)
            .finalRecipientField(finalRecipientField)
            .dispositionField(disposition)
            .gatewayField(gateway)
            .originalMessageIdField(originalMessageIdField)
            .originalRecipientField(originalRecipientField)
            .reportingUserAgentField(reportingUserAgentField)
            .addErrorField(errorField1)
            .addErrorField(errorField2)
            .build();

        assertThat(mdnReport)
            .isEqualTo(new MDNReport(
                Optional.of(reportingUserAgentField), Optional.of(gateway), Optional.of(originalRecipientField),
                finalRecipientField, Optional.of(originalMessageIdField), disposition,
                ImmutableList.of(errorField1, errorField2), ImmutableList.of(extensionField1, extensionField2)));
    }
}
