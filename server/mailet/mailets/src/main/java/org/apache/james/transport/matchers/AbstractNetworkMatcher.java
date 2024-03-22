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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * AbstractNetworkMatcher makes writing IP Address matchers easier.
 * </p>
 * <p>
 * This class extends the GenericMatcher, and as such, has access to the matcher
 * condition via GenericMatcher.getCondition().<br>
 * On initialization, the init method retrieves the condition from the defined
 * matcher and create a corresponding NetMatcher.<br>
 * The marcher condition has to respect the syntax waited by the NetMacher.
 * </p>
 * <p>
 * This abstract network matcher needs to be implemented by a concrete class.<br>
 * The implementing concrete class will call the matchNetwork method.
 * </p>
 * 
 * @see org.apache.james.dnsservice.library.netmatcher.NetMatcher
 */
public abstract class AbstractNetworkMatcher extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNetworkMatcher.class);

    private NetMatcher authorizedNetworks;

    private DNSService dnsServer;

    @Override
    public void init() throws MessagingException {
        if (getCondition() != null) {
            authorizedNetworks = new NetMatcher(getCondition(), dnsServer);
            LOGGER.info("Authorized addresses: {}", authorizedNetworks);
        }
    }

    protected boolean matchNetwork(String addr) {
        return authorizedNetworks != null && authorizedNetworks.matchInetNetwork(addr);
    }

    @Inject
    public void setDNSService(DNSService dnsService) {
        this.dnsServer = dnsService;
    }

}
