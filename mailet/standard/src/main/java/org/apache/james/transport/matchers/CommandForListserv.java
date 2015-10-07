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

import org.apache.mailet.base.GenericRecipientMatcher;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;

/**
 * Returns positive if the recipient is a command for a listserv.  For example,
 * if my listserv is james@list.working-dogs.com, this matcher will return true
 * for james-on@list.working-dogs.com and james-off@list.working-dogs.com.
 *
 */
public class CommandForListserv extends GenericRecipientMatcher {

    private MailAddress listservAddress;


    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() throws MessagingException {
        listservAddress = new MailAddress(getCondition());
    }


    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericRecipientMatcher#matchRecipient(org.apache.mailet.MailAddress)
     */
    public boolean matchRecipient(MailAddress recipient) {
        if (recipient.getDomain().equals(listservAddress.getDomain())) {
            if (recipient.getLocalPart().equals(listservAddress.getLocalPart() + "-on")
                || recipient.getLocalPart().equals(listservAddress.getLocalPart() + "-off")) {
                return true;
            }
        }
        return false;
    }
}
