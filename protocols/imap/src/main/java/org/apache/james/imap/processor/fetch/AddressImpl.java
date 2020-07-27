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
 * 
 */
package org.apache.james.imap.processor.fetch;

import org.apache.james.imap.message.response.FetchResponse;

final class AddressImpl implements FetchResponse.Envelope.Address {
    private final String atDomainList;
    private final String hostName;
    private final String mailboxName;
    private final String personalName;

    public AddressImpl(String atDomainList, String hostName, String mailboxName, String personalName) {
        this.atDomainList = atDomainList;
        this.hostName = hostName;
        this.mailboxName = mailboxName;
        this.personalName = personalName;
    }

    @Override
    public String getAtDomainList() {
        return atDomainList;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public String getMailboxName() {
        return mailboxName;
    }

    @Override
    public String getPersonalName() {
        return personalName;
    }
}