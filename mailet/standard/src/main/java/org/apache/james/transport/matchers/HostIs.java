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

import java.util.Collection;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Matches mail to given hosts.
 */
public class HostIs extends GenericRecipientMatcher {

    private Collection<String> hosts;

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() {
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        hosts = new Vector<String>();
        while (st.hasMoreTokens()) {
            hosts.add(st.nextToken().toLowerCase(Locale.US));
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericRecipientMatcher#matchRecipient(org.apache.mailet.MailAddress)
     */
    public boolean matchRecipient(MailAddress recipient) {
        return hosts.contains(recipient.getDomain().toLowerCase(Locale.US));
    }
}
