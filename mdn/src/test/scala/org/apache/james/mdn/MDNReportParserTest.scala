/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *    http://www.apache.org/licenses/LICENSE-2.0                *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.mdn

import org.apache.james.mdn.`type`.DispositionType
import org.apache.james.mdn.action.mode.DispositionActionMode
import org.apache.james.mdn.fields.{AddressType, Disposition, Error, ExtensionField, FinalRecipient, Gateway, OriginalMessageId, OriginalRecipient, ReportingUserAgent, Text}
import org.apache.james.mdn.modifier.DispositionModifier
import org.apache.james.mdn.sending.mode.DispositionSendingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MDNReportParserTest {

  @Test
  def parseShouldReturnEmptyWhenMissingFinalRecipient(): Unit = {
    val missing = "Disposition: automatic-action/MDN-sent-automatically;processed\r\n"
    val actual = MDNReportParser.parse(missing).toOption
    assertThat(actual.isEmpty)
  }

  @Test
  def parseShouldReturnMdnReportWhenMaximalSubset(): Unit = {
    val maximal = """Reporting-UA: UA_name; UA_product
      |MDN-Gateway: smtp; apache.org
      |Original-Recipient: rfc822; originalRecipient
      |Final-Recipient: rfc822; final_recipient
      |Original-Message-ID: <original@message.id>
      |Disposition: automatic-action/MDN-sent-automatically;processed/error,failed
      |Error: Message1
      |Error: Message2
      |X-OPENPAAS-IP: 177.177.177.77
      |X-OPENPAAS-PORT: 8000
      |""".replaceAllLiterally(System.lineSeparator(), "\r\n")
      .stripMargin
    val expected = Some(MDNReport.builder
      .reportingUserAgentField(ReportingUserAgent.builder
        .userAgentName("UA_name")
        .userAgentProduct("UA_product")
        .build)
      .gatewayField(Gateway.builder
        .nameType(new AddressType("smtp"))
        .name(Text.fromRawText("apache.org"))
        .build)
      .originalRecipientField("originalRecipient")
      .finalRecipientField("final_recipient")
      .originalMessageIdField("<original@message.id>")
      .dispositionField(Disposition.builder
        .actionMode(DispositionActionMode.Automatic)
        .sendingMode(DispositionSendingMode.Automatic)
        .`type`(DispositionType.Processed)
        .addModifier(DispositionModifier.Error)
        .addModifier(DispositionModifier.Failed)
        .build)
      .addErrorField("Message1")
      .addErrorField("Message2")
      .withExtensionField(ExtensionField.builder
        .fieldName("X-OPENPAAS-IP")
        .rawValue(" 177.177.177.77")
        .build)
      .withExtensionField(ExtensionField.builder
        .fieldName("X-OPENPAAS-PORT")
        .rawValue(" 8000")
        .build)
      .build)
    val actual = MDNReportParser.parse(maximal).toOption
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  def parseShouldReturnMdnReportWhenMinimalSubset(): Unit = {
    val minimal = """Final-Recipient: rfc822; final_recipient
      |Disposition: automatic-action/MDN-sent-automatically;processed
      |""".replaceAllLiterally(System.lineSeparator(), "\r\n")
      .stripMargin
    val disposition = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Automatic)
      .`type`(DispositionType.Processed)
      .build
    val expected = Some(MDNReport.builder
      .finalRecipientField("final_recipient")
      .dispositionField(disposition)
      .build)
    val actual = MDNReportParser.parse(minimal).toOption
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  def parseShouldReturnEmptyWhenDuplicatedFields(): Unit = {
    val duplicated = """Final-Recipient: rfc822; final_recipient
      |Final-Recipient: rfc822; final_recipient
      |Disposition: automatic-action/MDN-sent-automatically;processed
      |""".replaceAllLiterally(System.lineSeparator(), "\r\n")
      .stripMargin
    val actual = MDNReportParser.parse(duplicated).toOption
    assertThat(actual.isEmpty)
  }

  @Test
  def reportingUserAgentShouldParseWithoutProduct(): Unit = {
    val userAgent = "Reporting-UA: UA_name"
    val parser = new MDNReportParser(userAgent)
    val result = parser.reportingUaField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(ReportingUserAgent.builder.userAgentName("UA_name").build)
  }

  @Test
  def reportingUserAgentShouldParseWithProduct(): Unit = {
    val userAgent = "Reporting-UA: UA_name; UA_product"
    val parser = new MDNReportParser(userAgent)
    val result = parser.reportingUaField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(ReportingUserAgent.builder.userAgentName("UA_name").userAgentProduct("UA_product").build)
  }

  @Test
  def mdnGatewayFieldShouldParse(): Unit = {
    val gateway = "MDN-Gateway: smtp; apache.org"
    val parser = new MDNReportParser(gateway)
    val result = parser.mdnGatewayField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(Gateway.builder.nameType(new AddressType("smtp")).name(Text.fromRawText("apache.org")).build)
  }

  @Test
  def originalRecipientFieldShouldParse(): Unit = {
    val originalRecipient = "Original-Recipient: rfc822; originalRecipient"
    val parser = new MDNReportParser(originalRecipient)
    val result = parser.originalRecipientField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(OriginalRecipient.builder.addressType(new AddressType("rfc822")).originalRecipient(Text.fromRawText("originalRecipient")).build)
  }

  @Test
  def finalRecipientFieldShouldParse(): Unit = {
    val finalRecipient = "Final-Recipient: rfc822; final_recipient"
    val parser = new MDNReportParser(finalRecipient)
    val result = parser.finalRecipientField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(FinalRecipient.builder.addressType(new AddressType("rfc822")).finalRecipient(Text.fromRawText("final_recipient")).build)
  }

  @Test
  def originalMessageIdShouldParse(): Unit = {
    val originalMessageId = "Original-Message-ID: <original@message.id>"
    val parser = new MDNReportParser(originalMessageId)
    val result = parser.originalMessageIdField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(new OriginalMessageId("<original@message.id>"))
  }

  @Test
  def dispositionFieldShouldParseWhenMinimal(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-automatically;processed"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Automatic)
      .`type`(DispositionType.Processed)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenMaximal(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed"
    val expected = Disposition.builder.
      actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Automatic)
      .`type`(DispositionType.Processed)
      .addModifier(DispositionModifier.Error)
      .addModifier(DispositionModifier.Failed)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenManualAutomaticWithDisplayedType(): Unit = {
    val disposition = "Disposition: manual-action/MDN-sent-automatically;processed"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Manual)
      .sendingMode(DispositionSendingMode.Automatic)
      .`type`(DispositionType.Processed)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenAutomaticManualWithDisplayedType(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-manually;processed"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Manual)
      .`type`(DispositionType.Processed)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenDeletedType(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-manually;deleted"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Manual)
      .`type`(DispositionType.Deleted)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenDispatchedType(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-manually;dispatched"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Manual)
      .`type`(DispositionType.Dispatched)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def dispositionFieldShouldParseWhenDisplayedType(): Unit = {
    val disposition = "Disposition: automatic-action/MDN-sent-manually;displayed"
    val expected = Disposition.builder
      .actionMode(DispositionActionMode.Automatic)
      .sendingMode(DispositionSendingMode.Manual)
      .`type`(DispositionType.Displayed)
      .build
    val parser = new MDNReportParser(disposition)
    val result = parser.dispositionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(expected)
  }

  @Test
  def errorFieldShouldParse(): Unit = {
    val error = "Error: Message1"
    val parser = new MDNReportParser(error)
    val result = parser.errorField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(new Error(Text.fromRawText("Message1")))
  }

  @Test
  def extensionFieldShouldParse(): Unit = {
    val extension = "X-OPENPAAS-IP: 177.177.177.77"
    val parser = new MDNReportParser(extension)
    val result = parser.extentionField.run()
    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(ExtensionField.builder.fieldName("X-OPENPAAS-IP").rawValue(" 177.177.177.77").build)
  }
}