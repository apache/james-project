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

 /**
 * The `SanitizeMimeMessageId` mailet is designed to address a specific issue where some email clients, such as Outlook for Android, do not add the MIME `Message-ID` header to the emails they send.
 * The absence of the `Message-ID` header can cause emails to be rejected by downstream mail servers,
 * as required by RFC 5322 specifications.
 *
 * Sample configuration:
 *
 * <pre><code>
 * &lt;mailet match="All" class="SanitizeMimeMessageId"&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */

package org.apache.james.transport.mailets;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

public class SanitizeMimeMessageId extends GenericMailet {

    @Override
    public void service(Mail mail) throws MessagingException {
        if (mail.getMessage().getMessageID() == null) {
            mail.getMessage().saveChanges();
        }
    }

    @Override
    public String getMailetInfo() {
        return "SanitizeMimeMessageId Mailet";
    }
}
