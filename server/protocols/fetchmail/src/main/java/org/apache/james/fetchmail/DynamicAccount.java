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

package org.apache.james.fetchmail;

import jakarta.mail.Session;

import org.apache.commons.configuration2.ex.ConfigurationException;

public class DynamicAccount extends Account {

    /**
     * Constructor for DynamicAccount.
     * 
     * @param sequenceNumber
     * @param parsedConfiguration
     * @param user
     * @param password
     * @param recipient
     * @param ignoreRecipientHeader
     * @param session
     * @throws ConfigurationException
     */
    private DynamicAccount(int sequenceNumber, ParsedConfiguration parsedConfiguration, String user, String password, String recipient, boolean ignoreRecipientHeader, String customRecipientHeader, Session session) throws ConfigurationException {
        super(sequenceNumber, parsedConfiguration, user, password, recipient, ignoreRecipientHeader, customRecipientHeader, session);
    }

    /**
     * Constructor for DynamicAccount.
     * 
     * @param sequenceNumber
     * @param parsedConfiguration
     * @param userName
     * @param userPrefix
     * @param userSuffix
     * @param password
     * @param recipientPrefix
     * @param recipientSuffix
     * @param ignoreRecipientHeader
     * @param session
     * @throws ConfigurationException
     */
    public DynamicAccount(int sequenceNumber, ParsedConfiguration parsedConfiguration, String userName, String userPrefix, String userSuffix, String password, String recipientPrefix, String recipientSuffix, boolean ignoreRecipientHeader, String customRecipientHeader, Session session)
            throws ConfigurationException {
        this(sequenceNumber, parsedConfiguration, null, password, null, ignoreRecipientHeader, customRecipientHeader, session);

        StringBuilder userBuffer = new StringBuilder(userPrefix);
        userBuffer.append(userName);
        userBuffer.append(userSuffix);
        setUser(userBuffer.toString());

        StringBuilder recipientBuffer = new StringBuilder(recipientPrefix);
        recipientBuffer.append(userName);
        recipientBuffer.append(recipientSuffix);
        setRecipient(recipientBuffer.toString());
    }
}
