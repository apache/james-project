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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;

/**
 * This class can be used to reject email with bogus MX which is send from a
 * authorized user or an authorized network.
 */
public class ValidRcptMX implements RcptHook, ProtocolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidRcptMX.class);

    private DNSService dnsService = null;

    private static final String LOCALHOST = "localhost";

    private NetMatcher bNetwork = null;

    /**
     * Gets the DNS service.
     *
     * @return the dnsService
     */
    public final DNSService getDNSService() {
        return dnsService;
    }

    /**
     * Sets the DNS service.
     *
     * @param dnsService the dnsService to set
     */
    @Inject
    public final void setDNSService(DNSService dnsService) {
        this.dnsService = dnsService;
    }


    /**
     * Set the banned networks
     *
     * @param networks  Collection of networks
     * @param dnsServer The DNSServer
     */
    public void setBannedNetworks(Collection<String> networks, DNSService dnsServer) {
        bNetwork = new NetMatcher(networks, dnsServer);
    }

    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {

        String domain = rcpt.getDomain();

        // Email should be deliver local
        if (!domain.equals(LOCALHOST)) {

            Iterator<String> mx;
            try {
                mx = dnsService.findMXRecords(domain).iterator();
            } catch (TemporaryResolutionException e1) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }

            if (mx != null && mx.hasNext()) {
                while (mx.hasNext()) {
                    String mxRec = mx.next();

                    try {
                        String ip = dnsService.getByName(mxRec).getHostAddress();

                        // Check for invalid MX
                        if (bNetwork.matchInetNetwork(ip)) {
                            return new HookResult(HookReturnCode.DENY, SMTPRetCode.AUTH_REQUIRED, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH) + " Invalid MX " + session.getRemoteAddress().getAddress().toString() + " for domain " + domain + ". Reject email");
                        }
                    } catch (UnknownHostException e) {
                        // Ignore this
                    }
                }
            }
        }
        return new HookResult(HookReturnCode.DECLINED);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

        String[] networks = config.getStringArray("invalidMXNetworks");

        if (networks.length == 0) {

            Collection<String> bannedNetworks = Arrays.stream(networks)
                .map(String::trim)
                .collect(Guavate.toImmutableList());

            setBannedNetworks(bannedNetworks, dnsService);

            LOGGER.info("Invalid MX Networks: " + bNetwork.toString());
        } else {
            throw new ConfigurationException("Please configure at least on invalid MX network");
        }
    }

    @Override
    public void destroy() {
        // nothing to-do
    }
}
