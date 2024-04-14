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
package org.apache.james.smtpserver.fastfail;

import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.protocols.smtp.SMTPSession;

public class ValidSenderDomainHandler extends org.apache.james.protocols.smtp.core.fastfail.ValidSenderDomainHandler {
    private final DNSService dnsService;

    @Inject
    public ValidSenderDomainHandler(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    protected boolean hasMXRecord(SMTPSession session, String domain) {
        // null sender so return
        if (domain == null) {
            return false;
        }

        Collection<String> records = null;
            
            // try to resolv the provided domain in the senderaddress. If it can not resolved do not accept it.
            try {
                records = dnsService.findMXRecords(domain);
            } catch (org.apache.james.dnsservice.api.TemporaryResolutionException e) {
                // TODO: Should we reject temporary ?
            }

        return !(records == null || records.size() == 0);

    }
}
