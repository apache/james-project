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



package org.apache.james.transport.matchers;

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import java.util.Collection;

/**
 * <P>
 * Matches mails that are sent by an client which is allowed to relay.
 * </P>
 * 
 * <PRE><CODE> &lt;mailet match=&quot;SMTPIsAuthNetwork&quot;
 * class=&quot;&lt;any-class&gt;&quot;&gt; </CODE></PRE>
 * 
 */
public class SMTPIsAuthNetwork extends GenericMatcher {

    /**
     * The mail attribute which is set if the client is allowed to relay
     */
    private final static String SMTP_AUTH_NETWORK_NAME = "org.apache.james.SMTPIsAuthNetwork";

    public Collection<MailAddress> match(Mail mail) {
        String relayingAllowed = (String) mail
                .getAttribute(SMTP_AUTH_NETWORK_NAME);
        if (relayingAllowed != null && relayingAllowed.equals("true")) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
