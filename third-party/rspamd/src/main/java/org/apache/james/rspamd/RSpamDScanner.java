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

package org.apache.james.rspamd;


import java.util.List;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.rspamd.client.RSpamDHttpClient;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.GenericMailet;

import com.google.common.collect.ImmutableList;

public class RSpamDScanner extends GenericMailet {
    public static final AttributeName FLAG_MAIL = AttributeName.of("org.apache.james.rspamd.flag");
    public static final AttributeName STATUS_MAIL = AttributeName.of("org.apache.james.rspamd.status");

    private final RSpamDHttpClient rSpamDHttpClient;

    @Inject
    public RSpamDScanner(RSpamDHttpClient rSpamDHttpClient) {
        this.rSpamDHttpClient = rSpamDHttpClient;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        AnalysisResult rSpamDResult = rSpamDHttpClient.checkV2(new MimeMessageInputStream(mail.getMessage())).block();

        mail.getRecipients()
            .forEach(recipient -> appendRSpamDResultHeader(mail, recipient, rSpamDResult));
    }

    private void appendRSpamDResultHeader(Mail mail, MailAddress recipient, AnalysisResult rSpamDResult) {
        for (Attribute attribute : getHeadersAsAttributes(rSpamDResult)) {
            mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(attribute.getName().asString())
                .value((String) attribute.getValue().value())
                .build(), recipient);
        }
    }

    private List<Attribute> getHeadersAsAttributes(AnalysisResult rSpamDResult) {
        String defaultFlagMailAttributeValue = "NO";
        String defaultStatusMailAttributeValue = "No";
        if (rSpamDResult.getAction().equals(AnalysisResult.Action.REJECT)) {
            defaultFlagMailAttributeValue = "YES";
            defaultStatusMailAttributeValue = "Yes";
        }

        return ImmutableList.of(new Attribute(FLAG_MAIL, AttributeValue.of(defaultFlagMailAttributeValue)),
            new Attribute(STATUS_MAIL, AttributeValue.of(defaultStatusMailAttributeValue + ","
                + " actions=" + rSpamDResult.getAction().getDescription()
                + " score=" + rSpamDResult.getScore()
                + " requiredScore=" + rSpamDResult.getRequiredScore())));
    }
}
