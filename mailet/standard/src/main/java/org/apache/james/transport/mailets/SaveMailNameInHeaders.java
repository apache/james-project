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

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save Mail name into X-JAMES-Mail-Name header.
 *
 * This allows from mailetcontainer logs to find the mail in user mailbox.
 *
 * Eg:
 *
 * <pre><code>
 * &lt;mailet match="All" class="SaveMailNameInHeaders"/&gt;
 * </code></pre>
 */
public class SaveMailNameInHeaders extends GenericMailet {

    public static final Logger LOGGER = LoggerFactory.getLogger(SaveMailNameInHeaders.class);

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            mail.getMessage().setHeader("X-JAMES-Mail-Name", mail.getName());
            mail.getMessage().saveChanges();
        } catch (Exception e) {
            LOGGER.error("Cannot record mail name in headers", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "SaveMailNameInHeaders Mailet";
    }
}
