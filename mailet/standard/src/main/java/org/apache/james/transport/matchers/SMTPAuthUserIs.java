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
import java.util.StringTokenizer;

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * <P>Matches mails that are sent by an SMTP authenticated user present in a supplied list.</P>
 * <P>If the sender was not authenticated it will not match.</P>
 * <P>Configuration string: a comma, tab or space separated list of James users.</P>
 * <PRE><CODE>
 * &lt;mailet match=&quot;SMTPAuthUserIs=&lt;list-of-user-names&gt;&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * </CODE></PRE>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class SMTPAuthUserIs extends GenericMatcher {
    
    /**
     * The mail attribute holding the SMTP AUTH user name, if any.
     */
    private final static String SMTP_AUTH_USER_ATTRIBUTE_NAME = "org.apache.james.SMTPAuthUser";
    
    private Collection<String> users;

    public void init() throws javax.mail.MessagingException {
        StringTokenizer st = new StringTokenizer(getCondition(), ", \t", false);
        users = new java.util.HashSet<String>();
        while (st.hasMoreTokens()) {
            users.add(st.nextToken());
        }
    }

    public Collection<MailAddress> match(Mail mail) {
        String authUser = (String) mail.getAttribute(SMTP_AUTH_USER_ATTRIBUTE_NAME);
        if (authUser != null && users.contains(authUser)) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }
}
