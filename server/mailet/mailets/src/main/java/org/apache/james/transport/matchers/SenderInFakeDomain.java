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

import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does a DNS lookup (MX and A/CNAME records) on the sender's domain. If there
 * are no entries, the domain is considered fake and the match is successful.
 */
@Experimental
public class SenderInFakeDomain extends AbstractNetworkMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SenderInFakeDomain.class);

    public Collection<MailAddress> match(Mail mail) {
        if (mail.getSender() == null) {
            return null;
        }
        String domain = mail.getSender().getDomain();
        // DNS Lookup for this domain
        @SuppressWarnings("deprecation")
        Collection<String> servers = getMailetContext().getMailServers(domain);
        if (servers.size() == 0) {
            // No records...could not deliver to this domain, so matches
            // criteria.
            LOGGER.info("No MX, A, or CNAME record found for domain: " + domain);
            return mail.getRecipients();
        } else if (matchNetwork(servers.iterator().next())) {
            /*
             * It could be a wildcard address like these:
             *
             * 64.55.105.9/32          # Allegiance Telecom Companies Worldwide (.nu)
             * 64.94.110.11/32         # VeriSign (.com .net)
             * 194.205.62.122/32       # Network Information Center - Ascension Island (.ac)
             * 194.205.62.62/32        # Internet Computer Bureau (.sh)
             * 195.7.77.20/32          # Fredrik Reutersward Data (.museum)
             * 206.253.214.102/32      # Internap Network Services (.cc)
             * 212.181.91.6/32         # .NU Domain Ltd. (.nu)
             * 219.88.106.80/32        # Telecom Online Solutions (.cx)
             * 194.205.62.42/32        # Internet Computer Bureau (.tm)
             * 216.35.187.246/32       # Cable & Wireless (.ws)
             * 203.119.4.6/32          # .PH TLD (.ph)
             *
             */
            LOGGER.info("Banned IP found for domain: " + domain);
            LOGGER.info(" --> :" + servers.iterator().next());
            return mail.getRecipients();
        } else {
            // Some servers were found... the domain is not fake.

            return null;
        }
    }
}
