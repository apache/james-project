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

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.protocols.api.handler.ProtocolHandler;

public class DNSRBLHandler extends org.apache.james.protocols.smtp.core.fastfail.DNSRBLHandler implements ProtocolHandler {

    private DNSService dns;

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        boolean validConfig = false;
        HierarchicalConfiguration handlerConfiguration = (HierarchicalConfiguration) config;
        ArrayList<String> rblserverCollection = new ArrayList<String>();

        Collections.addAll(rblserverCollection, handlerConfiguration.getStringArray("rblservers.whitelist"));
        if (rblserverCollection.size() > 0) {
            setWhitelist(rblserverCollection.toArray(new String[rblserverCollection.size()]));
            rblserverCollection.clear();
            validConfig = true;
        }
        Collections.addAll(rblserverCollection, handlerConfiguration.getStringArray("rblservers.blacklist"));
        if (rblserverCollection.size() > 0) {
            setBlacklist(rblserverCollection.toArray(new String[rblserverCollection.size()]));
            rblserverCollection.clear();
            validConfig = true;
        }

        // Throw an ConfiigurationException on invalid config
        if (!validConfig) {
            throw new ConfigurationException("Please configure whitelist or blacklist");
        }

        setGetDetail(handlerConfiguration.getBoolean("getDetail", false));
    }

    @Override
    public void destroy() {
        // Do nothing
    }

    @Override
    protected boolean resolve(String ip) {
        try {
            dns.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Override
    protected Collection<String> resolveTXTRecords(String ip) {
        return dns.findTXTRecords(ip);
    }
}
