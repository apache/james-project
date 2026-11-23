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

import java.time.Duration;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.util.DurationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This ConnectHandler can be used to activate pop-before-smtp
 *
 * @deprecated POP-before-SMTP grants relaying to an IP address, and the expiry of the grant is not enforced on every
 * connection. Use SMTP authentication instead.
 */
@Deprecated
public class POP3BeforeSMTPHandler implements ConnectHandler<SMTPSession>, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(POP3BeforeSMTPHandler.class);

    /** The time after which ipAddresses should be handled as expired */
    private Duration expireTime = POP3BeforeSMTPHelper.EXPIRE_TIME;

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        LOGGER.error("POP3BeforeSMTPHandler is deprecated: POP-before-SMTP grants relaying to an IP address, and the expiry of "
            + "the grant is not enforced on every connection. Use SMTP authentication instead.");
        try {
            setExpireTime(config.getString("expireTime", null));
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Please configure a valid expireTime", e);
        }
    }

    /**
     * Set the time after which an ipAddresses should be handled as expired
     * 
     * @param rawExpireTime
     *            The time
     */
    public void setExpireTime(String rawExpireTime) {
        if (rawExpireTime != null) {
            this.expireTime = DurationParser.parse(rawExpireTime);
        }
    }

    @Override
    public Response onConnect(SMTPSession session) {

        // some kind of random cleanup process
        if (Math.random() > 0.99) {
            POP3BeforeSMTPHelper.removeExpiredIP(expireTime);
        }

        // Check if the ip is allowed to relay
        if (!session.isRelayingAllowed() && POP3BeforeSMTPHelper.isAuthorized(session.getRemoteAddress().getAddress().getHostAddress())) {
            session.setRelayingAllowed(true);
        }
        return null;
    }
}
