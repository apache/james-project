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

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.james.util.FunctionalUtils;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

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
    private static final AttributeName SMTP_AUTH_NETWORK_NAME = AttributeName.of("org.apache.james.SMTPIsAuthNetwork");

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return AttributeUtils
            .getValueAndCastFromMail(mail, SMTP_AUTH_NETWORK_NAME, Boolean.class)
            .filter(FunctionalUtils.identityPredicate())
            .map(any -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }

    public static Attribute makeAttribute(Boolean value) {
        return new Attribute(SMTP_AUTH_NETWORK_NAME, AttributeValue.of(value));
    }
}
