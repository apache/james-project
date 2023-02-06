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


package org.apache.james.protocols.smtp;

import java.util.Optional;
import java.util.Properties;

import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.api.ProtocolConfigurationImpl;

/**
 * {@link SMTPConfiguration} implementation which allows to set and get various configuration params. The set and get methods
 * are not thread-safe
 */
public class SMTPConfigurationImpl extends ProtocolConfigurationImpl implements SMTPConfiguration {
    private final long maxMessageSize = 0;
    private boolean bracketsEnforcement = true;
    private boolean enforceHeloEhlo = true;

    public SMTPConfigurationImpl() {
        super("JAMES SMTP Protocols Server");
    }
    
    @Override
    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Return <code>false</code>
     */
    @Override
    public boolean isRelayingAllowed(String remoteIP) {
        return false;
    }

    /**
     * Return <code>false</code>
     */
    @Override
    public boolean isAuthAnnounced(String remoteIP, boolean tlsStarted) {
        return false;
    }

    public void setHeloEhloEnforcement(boolean enforceHeloEhlo) {
        this.enforceHeloEhlo = enforceHeloEhlo;
    }
    
    
    @Override
    public boolean useHeloEhloEnforcement() {
        return enforceHeloEhlo;
    }
    
    
    @Override
    public boolean useAddressBracketsEnforcement() {
        return bracketsEnforcement;
    }

    
    public void setUseAddressBracketsEnforcement(boolean bracketsEnforcement) {
        this.bracketsEnforcement = bracketsEnforcement;
    }

    @Override
    public boolean isPlainAuthEnabled() {
        return true;
    }

    @Override
    public Optional<OidcSASLConfiguration> saslConfiguration() {
        return Optional.empty();
    }

    @Override
    public Properties customProperties() {
        return null;
    }
}
