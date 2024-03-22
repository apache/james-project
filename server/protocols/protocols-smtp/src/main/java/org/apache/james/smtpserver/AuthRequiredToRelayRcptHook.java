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
package org.apache.james.smtpserver;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.smtp.core.AbstractAuthRequiredToRelayRcptHook;

public class AuthRequiredToRelayRcptHook extends AbstractAuthRequiredToRelayRcptHook {
    private final DomainList domains;

    @Inject
    public AuthRequiredToRelayRcptHook(DomainList domains) {
        this.domains = domains;
    }

    @Override
    protected boolean isLocalDomain(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            return false;
        }
    }
}
