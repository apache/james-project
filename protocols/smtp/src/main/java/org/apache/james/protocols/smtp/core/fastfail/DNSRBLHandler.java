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
package org.apache.james.protocols.smtp.core.fastfail;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.StringTokenizer;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
  * Connect handler for DNSRBL processing
  */
public class DNSRBLHandler implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(DNSRBLHandler.class);

    /**
     * The lists of rbl servers to be checked to limit spam
     */
    private String[] whitelist;
    private String[] blacklist;
        
    private boolean getDetail = false;
    
    private final String blocklistedDetail = null;
    
    public static final String RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.blocklisted";
    
    public static final String RBL_DETAIL_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.detail";

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * Set the whitelist array
     * 
     * @param whitelist The array which contains the whitelist
     */
    public void setWhitelist(String[] whitelist) {
        // We need to copy the String array because of possible security issues.
        // Similar to https://issues.apache.org/jira/browse/PROTOCOLS-18
        if (whitelist != null) {
            this.whitelist = new String[whitelist.length];
            for (int i = 0; i < whitelist.length; i++) {
                this.whitelist[i] = new String(whitelist[i]);
            }
        }
        this.whitelist = whitelist;
    }
    
    /**
     * Set the blacklist array
     * 
     * @param blacklist The array which contains the blacklist
     */
    public void setBlacklist(String[] blacklist) {
        // We need to copy the String array because of possible security issues.
        // Similar to https://issues.apache.org/jira/browse/PROTOCOLS-18
        if (blacklist != null) {
            this.blacklist = new String[blacklist.length];
            for (int i = 0; i < blacklist.length; i++) {
                this.blacklist[i] = new String(blacklist[i]);
            }
        }
    }

    /**
     * Set for try to get a TXT record for the blocked record. 
     * 
     * @param getDetail Set to ture for enable
     */
    public void setGetDetail(boolean getDetail) {
        this.getDetail = getDetail;
    }

    /**
     *
     * This checks DNSRBL whitelists and blacklists.  If the remote IP is whitelisted
     * it will be permitted to send e-mail, otherwise if the remote IP is blacklisted,
     * the sender will only be permitted to send e-mail to postmaster (RFC 2821) or
     * abuse (RFC 2142), unless authenticated.
     */
    public void checkDNSRBL(SMTPSession session, String ipAddress) {
        
        /*
         * don't check against rbllists if the client is allowed to relay..
         * This whould make no sense.
         */
        if (session.isRelayingAllowed()) {
            LOGGER.info("Ipaddress {} is allowed to relay. Don't check it", session.getRemoteAddress().getAddress());
            return;
        }
        
        if (whitelist != null || blacklist != null) {
            StringBuffer sb = new StringBuffer();
            StringTokenizer st = new StringTokenizer(ipAddress, " .", false);
            while (st.hasMoreTokens()) {
                sb.insert(0, st.nextToken() + ".");
            }
            String reversedOctets = sb.toString();

            if (whitelist != null) {
                String[] rblList = whitelist;
                for (String rbl : rblList) {
                    if (resolve(reversedOctets + rbl)) {
                        LOGGER.info("Connection from {} whitelisted by {}", ipAddress, rbl);
                        return;
                    } else {
                        LOGGER.debug("IpAddress {} not listed on {}", session.getRemoteAddress().getAddress(), rbl);
                    }
                }
            }

            if (blacklist != null) {
                String[] rblList = blacklist;
                for (String rbl : rblList) {
                    if (resolve(reversedOctets + rbl)) {
                        LOGGER.info("Connection from {} restricted by {} to SMTP AUTH/postmaster/abuse.", ipAddress, rbl);

                        // we should try to retrieve details
                        if (getDetail) {
                            Collection<String> txt = resolveTXTRecords(reversedOctets + rbl);

                            // Check if we found a txt record
                            if (!txt.isEmpty()) {
                                // Set the detail
                                String blocklistedDetail = txt.iterator().next().toString();

                                session.setAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, blocklistedDetail, State.Connection);
                            }
                        }

                        session.setAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, "true", State.Connection);
                        return;
                    } else {
                        // if it is unknown, it isn't blocked
                        LOGGER.debug("unknown host exception thrown: {}", rbl);
                    }

                }
            }
        }
    }
    
    @Override
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        checkDNSRBL(session, session.getRemoteAddress().getAddress().getHostAddress());
    
        if (!session.isRelayingAllowed()) {
            String blocklisted = (String) session.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection);
    
            if (blocklisted != null) { // was found in the RBL
                if (blocklistedDetail == null) {
                    return HookResult.builder()
                        .hookReturnCode(HookReturnCode.deny())
                        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
                            + " Rejected: unauthenticated e-mail from " + session.getRemoteAddress().getAddress()
                            + " is restricted.  Contact the postmaster for details.")
                        .build();
                } else {

                    return HookResult.builder()
                        .hookReturnCode(HookReturnCode.deny())
                        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.SECURITY_AUTH) + " " + blocklistedDetail)
                        .build();
                }
               
            }
        }
        return HookResult.DECLINED;
    }

    /**
     * Check if the given ipaddress is resolvable. 
     * 
     * This implementation use {@link InetAddress#getByName(String)}. Sub-classes may override this with a more performant solution
     * 
     * @param ip
     * @return canResolve
     */
    protected boolean resolve(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Return a {@link Collection} which holds all TXT records for the ip. This is most times used to add details for a RBL entry.
     * 
     * This implementation always returns an empty {@link Collection}. Sub-classes may override this.
     * 
     * @param ip
     * @return txtRecords
     */
    protected Collection<String> resolveTXTRecords(String ip) {
        return Collections.<String>emptyList();
    }
}
