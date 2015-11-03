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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.handler.ProtocolHandler;

import java.util.Arrays;

public class SpamTrapHandler extends org.apache.james.protocols.smtp.core.fastfail.SpamTrapHandler implements ProtocolHandler {

    @Override
    public void init(Configuration config) throws ConfigurationException {
        String[] rcpts = config.getStringArray("spamTrapRecip");

        if (rcpts.length == 0) {
            setSpamTrapRecipients(Arrays.asList(rcpts));
        } else {
            throw new ConfigurationException("Please configure a spamTrapRecip.");
        }

        setBlockTime(config.getLong("blockTime", blockTime));
    }

    @Override
    public void destroy() {
        // nothing to-do
    }
}
