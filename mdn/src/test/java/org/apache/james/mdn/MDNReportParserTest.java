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

import org.apache.james.mdn.MDNReportParser.Parser;
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
import org.junit.Test;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

public class MDNReportParserTest {

    @Test
    public void parseShouldReturnEmptyWhenMissingFinalRecipient() {
        String missing = "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        MDNReportParser testee = new MDNReportParser();
        assertThat(testee.parse(missing)).isEmpty();
    }

    @Test
    public void parseShouldReturnMdnReportWhenMaximalSubset() {
        String maximal = "Reporting-UA: UA_name; UA_product\r\n" +
            "MDN-Gateway: smtp; apache.org\r\n" +
            "Original-Recipient: rfc822; originalRecipient\r\n" +
            "Final-Recipient: rfc822; final_recipient\r\n" +
            "Original-Message-ID: <original@message.id>\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
            "Error: Message1\r\n" +
            "Error: Message2\r\n" +
            "X-OPENPAAS-IP: 177.177.177.77\r\n" +
            "X-OPENPAAS-PORT: 8000\r\n";
        Optional<MDNReport> expected = Optional.of(MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder()
                .userAgentName("UA_name")
                .userAgentProduct("UA_product")
                .build())
            .gatewayField(Gateway.builder()
                .nameType(new AddressType("smtp"))
                .name(Text.fromRawText("apache.org"))
                .build())
            .originalRecipientField("originalRecipient")
            .finalRecipientField("final_recipient")
            .originalMessageIdField("<original@message.id>")
            .dispositionField(Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .addModifier(DispositionModifier.Error)
                .addModifier(DispositionModifier.Failed)
                .build())
            .addErrorField("Message1")
            .addErrorField("Message2")
            .withExtensionField(ExtensionField.builder()
                .fieldName("X-OPENPAAS-IP")
                .rawValue(" 177.177.177.77")
                .build())
            .withExtensionField(ExtensionField.builder()
                .fieldName("X-OPENPAAS-PORT")
                .rawValue(" 8000")
                .build())
            .build());
        MDNReportParser testee = new MDNReportParser();
        Optional<MDNReport> actual = testee.parse(maximal);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void parseShouldReturnMdnReportWhenMinimalSubset() {
        String minimal = "Final-Recipient: rfc822; final_recipient\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        Optional<MDNReport> expected = Optional.of(MDNReport.builder()
            .finalRecipientField("final_recipient")
            .dispositionField(Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .build())
            .build());
        MDNReportParser testee = new MDNReportParser();
        Optional<MDNReport> actual = testee.parse(minimal);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void parseShouldReturnEmptyWhenDuplicatedFields() {
        String duplicated = "Final-Recipient: rfc822; final_recipient\r\n" +
            "Final-Recipient: rfc822; final_recipient\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        MDNReportParser testee = new MDNReportParser();
        assertThat(testee.parse(duplicated)).isEmpty();
    }

    @Test
    public void reportingUserAgentShouldParseWithoutProduct() {
        String minimal = "Reporting-UA: UA_name";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.reportingUaField()).run(minimal);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(ReportingUserAgent.class);
        assertThat((ReportingUserAgent)result.resultValue).isEqualTo(ReportingUserAgent.builder().userAgentName("UA_name").build());
    }

    @Test
    public void reportingUserAgentShouldParseWithProduct() {
        String minimal = "Reporting-UA: UA_name; UA_product";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.reportingUaField()).run(minimal);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(ReportingUserAgent.class);
        assertThat((ReportingUserAgent)result.resultValue).isEqualTo(ReportingUserAgent.builder().userAgentName("UA_name").userAgentProduct("UA_product").build());
    }

    @Test
    public void mdnGatewayFieldShouldParse() {
        String gateway = "MDN-Gateway: smtp; apache.org";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.mdnGatewayField()).run(gateway);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Gateway.class);
        assertThat((Gateway)result.resultValue).isEqualTo(Gateway.builder().nameType(new AddressType("smtp")).name(Text.fromRawText("apache.org")).build());
    }

    @Test
    public void originalRecipientFieldShouldParse() {
        String originalRecipient = "Original-Recipient: rfc822; originalRecipient";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.originalRecipientField()).run(originalRecipient);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(OriginalRecipient.class);
        assertThat((OriginalRecipient)result.resultValue).isEqualTo(OriginalRecipient.builder().addressType(new AddressType("rfc822")).originalRecipient(Text.fromRawText("originalRecipient")).build());
    }

    @Test
    public void finalRecipientFieldShouldParse() {
        String finalRecipient = "Final-Recipient: rfc822; final_recipient";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.finalRecipientField()).run(finalRecipient);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(FinalRecipient.class);
        assertThat((FinalRecipient)result.resultValue).isEqualTo(FinalRecipient.builder().addressType(new AddressType("rfc822")).finalRecipient(Text.fromRawText("final_recipient")).build());
    }

    @Test
    public void originalMessageIdShouldParse() {
        String originalMessageId = "Original-Message-ID: <original@message.id>";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.originalMessageIdField()).run(originalMessageId);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(OriginalMessageId.class);
        assertThat((OriginalMessageId)result.resultValue).isEqualTo(new OriginalMessageId("<original@message.id>"));
    }

    @Test
    public void dispositionFieldShouldParseWhenMinimal() {
        String minimal = "Disposition: automatic-action/MDN-sent-automatically;processed";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(minimal);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenMaximal() {
        String maximal = "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed";
        Disposition expected = Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .addModifier(DispositionModifier.Error)
                .addModifier(DispositionModifier.Failed)
                .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(maximal);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenManualAutomaticWithDisplayedType() {
        String disposition = "Disposition: manual-action/MDN-sent-automatically;processed";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Manual)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Processed)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(disposition);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenAutomaticManualWithDisplayedType() {
        String disposition = "Disposition: automatic-action/MDN-sent-manually;processed";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Processed)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(disposition);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenDeletedType() {
        String disposition = "Disposition: automatic-action/MDN-sent-manually;deleted";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Deleted)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(disposition);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenDispatchedType() {
        String disposition = "Disposition: automatic-action/MDN-sent-manually;dispatched";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Dispatched)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(disposition);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void dispositionFieldShouldParseWhenDisplayedType() {
        String disposition = "Disposition: automatic-action/MDN-sent-manually;displayed";
        Disposition expected = Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Manual)
            .type(DispositionType.Displayed)
            .build();
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionField()).run(disposition);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Disposition.class);
        assertThat((Disposition)result.resultValue).isEqualTo(expected);
    }

    @Test
    public void errorFieldShouldParse() {
        String error = "Error: Message1";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.errorField()).run(error);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(Error.class);
        assertThat((Error)result.resultValue).isEqualTo(new Error(Text.fromRawText("Message1")));
    }

    @Test
    public void extensionFieldShouldParse() {
        String extension = "X-OPENPAAS-IP: 177.177.177.77";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.extentionField()).run(extension);
        assertThat(result.matched).isTrue();
        assertThat(result.resultValue).isInstanceOf(ExtensionField.class);
        assertThat((ExtensionField)result.resultValue).isEqualTo(ExtensionField.builder().fieldName("X-OPENPAAS-IP").rawValue(" 177.177.177.77").build());
    }
}
