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

import org.apache.james.mdn.MDNReportParser.Parser;
import org.apache.james.mdn.fields.AddressType;
import org.apache.james.mdn.fields.Gateway;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.fields.Text;
import org.junit.Test;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

public class MDNReportParserTest {

    @Test
    public void dispositionNotificationContentShouldNotParseWhenMissingFinalRecipient() {
        String missing = "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionNotificationContent()).run(missing);
        assertThat(result.matched).isFalse();
    }

    @Test
    public void dispositionNotificationContentShouldParseWhenMaximalSubset() {
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
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionNotificationContent()).run(maximal);
        assertThat(result.matched).isTrue();
    }

    @Test
    public void dispositionNotificationContentShouldParseWhenMinimalSubset() {
        String minimal = "Final-Recipient: rfc822; final_recipient\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionNotificationContent()).run(minimal);
        assertThat(result.matched).isTrue();
    }

    @Test
    public void dispositionNotificationContentShouldNotParseWhenDuplicatedFields() {
        String duplicated = "Final-Recipient: rfc822; final_recipient\r\n" +
            "Final-Recipient: rfc822; final_recipient\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;processed\r\n";
        Parser parser = Parboiled.createParser(MDNReportParser.Parser.class);
        ParsingResult<Object> result = new ReportingParseRunner<>(parser.dispositionNotificationContent()).run(duplicated);
        assertThat(result.matched).isFalse();
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
}
