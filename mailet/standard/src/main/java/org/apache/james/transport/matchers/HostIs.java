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
import java.util.Vector;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.mailet.base.GenericRecipientMatcher;

/**
 * Matches mail to given hosts.
 */
public class HostIs extends GenericRecipientMatcher {

    private Collection<Domain> hosts;

    @Override
    public void init() {
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        hosts = new Vector<>();
        while (st.hasMoreTokens()) {
            hosts.add(Domain.of(st.nextToken()));
        }
    }

    @Override
    public boolean matchRecipient(MailAddress recipient) {
        return hosts.contains(recipient.getDomain());
    }
}
