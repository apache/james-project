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

package mailets;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class SendPromotionCode extends GenericMailet {

    public static final boolean REPLY_TO_SENDER_ONLY = false;

    private String reason;
    private String promotionCode;

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage response = (MimeMessage) mail.getMessage()
            .reply(REPLY_TO_SENDER_ONLY);

        response.setText(reason + "\n\n" +
            "Here is the following promotion code that you can use on your next order: " + promotionCode);

        MailAddress sender = getMailetContext().getPostmaster();
        ImmutableList<MailAddress> recipients = mail.getMaybeSender().asList();

        getMailetContext()
            .sendMail(sender, recipients, response);
    }

    @Override
    public void init() throws MessagingException {
        reason = getInitParameter("reason");
        promotionCode = getInitParameter("promotionCode");

        if (Strings.isNullOrEmpty(reason)) {
            throw new MessagingException("'reason' is compulsory");
        }
        if (Strings.isNullOrEmpty(promotionCode)) {
            throw new MessagingException("'promotionCode' is compulsory");
        }
    }

    @Override
    public String getMailetName() {
        return "SendPromotionCode";
    }
}
