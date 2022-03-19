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

package org.apache.james.transport.mailets;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.GenericMailet;

/**
 * This mailet adds the de-facto standard QMail Delivered-To header.
 *
 * Upon processing by LocalDelivery, a Delivered-To header matching the recipient mail address will be added before storage.
 *
 * <pre><code>
 * &lt;mailet match=&quot;All&quot; class=&quot;&lt;AddDeliveredToHeader&gt;&quot;/&gt;
 * </code></pre>
 */
public class AddDeliveredToHeader extends GenericMailet {

    public static final String DELIVERED_TO = "Delivered-To";

    @Override
    public void service(Mail mail) throws MessagingException {
        for (MailAddress recipient: mail.getRecipients()) {
            mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(DELIVERED_TO)
                .value(recipient.asString())
                .build(), recipient);
        }
    }
}
