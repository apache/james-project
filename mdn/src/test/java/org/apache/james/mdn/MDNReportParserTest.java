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
}
