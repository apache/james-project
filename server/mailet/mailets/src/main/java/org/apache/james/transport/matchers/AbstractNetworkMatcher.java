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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.mailet.base.GenericMatcher;

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
 * The implementing concrete class will call the allowedNetworks or matchNetwork
 * methods.
 * </p>
 * 
 * @see org.apache.james.dnsservice.library.netmatcher.NetMatcher
 */
public abstract class AbstractNetworkMatcher extends GenericMatcher {

    /**
     * This is a Network Matcher that should be configured to contain authorized
     * networks
     */
    private NetMatcher authorizedNetworks = null;

    /**
     * The DNSService
     */
    private DNSService dnsServer;

    public void init() throws MessagingException {

        Collection<String> nets = allowedNetworks();

        if (nets != null) {
            authorizedNetworks = new NetMatcher(allowedNetworks(), dnsServer) {
                protected void log(String s) {
                    AbstractNetworkMatcher.this.log(s);
                }
            };
            log("Authorized addresses: " + authorizedNetworks.toString());
        }
    }

    protected Collection<String> allowedNetworks() {
        Collection<String> networks = null;
        if (getCondition() != null) {
            StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
            networks = new java.util.ArrayList<String>();
            while (st.hasMoreTokens())
                networks.add(st.nextToken());
        }
        return networks;
    }

    protected boolean matchNetwork(String addr) {
        return authorizedNetworks != null && authorizedNetworks.matchInetNetwork(addr);
    }

    /**
     * Injection setter for the DNSService.
     * 
     * @param dnsService
     */
    @Inject
    public void setDNSService(DNSService dnsService) {
        this.dnsServer = dnsService;
    }

}
