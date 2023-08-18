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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * This handler can be used for providing a spam trap. IPAddresses which send emails to the configured
 * recipients will get blacklisted for the configured time.
 */
public class SpamTrapHandler implements RcptHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpamTrapHandler.class);

    /** Map which hold blockedIps and blockTime in memory */
    private final Map<String,Long> blockedIps;
    private final Clock clock;
    
    private Collection<String> spamTrapRecips = new ArrayList<>();
    
    /** Default blocktime 12 hours */
    protected long blockTime = 4320000;

    @Inject
    public SpamTrapHandler() {
        this(Clock.systemUTC());
    }

    @VisibleForTesting
    public SpamTrapHandler(Clock clock) {
        this.blockedIps = new ConcurrentHashMap<>();
        this.clock = clock;
    }

    public void setSpamTrapRecipients(Collection<String> spamTrapRecips) {
        this.spamTrapRecips = spamTrapRecips;
    }
    
    public void setBlockTime(long blockTime) {
        this.blockTime = blockTime;
    }
    
    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt) {
        String address = session.getRemoteAddress().getAddress().getHostAddress();
        if (isBlocked(address)) {
            return HookResult.DENY;
        } else {
         
            if (spamTrapRecips.contains(rcpt.toString().toLowerCase(Locale.US))) {
        
                addIp(address);
            
                return HookResult.DENY;
            }
        }
        return HookResult.DECLINED;
    }
    
    
    /**
     * Check if ipAddress is in the blockList.
     * 
     * @param ip ipAddress to check
     * @return true or false
     */
    private boolean isBlocked(String ip) {
        Long rawTime = blockedIps.get(ip);
    
        if (rawTime != null) {
            long blockTime = rawTime;
           
            if (blockTime > clock.millis()) {
                LOGGER.debug("BlockList contain Ip {}", ip);
                return true;
            } else {
                LOGGER.debug("Remove ip {} from blockList", ip);

                blockedIps.remove(ip);
            }
        }
        return false;
    }
    
    /**
     * Add ipaddress to blockList
     * 
     * @param ip IpAddress to add
     */
    private void addIp(String ip) {
        long bTime = clock.millis() + blockTime;
        
        LOGGER.debug("Add ip {} for {} to blockList", ip, bTime);

        blockedIps.put(ip, bTime);
    }
}
