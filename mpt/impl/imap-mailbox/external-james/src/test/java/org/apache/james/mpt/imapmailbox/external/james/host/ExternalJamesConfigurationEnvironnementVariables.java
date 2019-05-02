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
package org.apache.james.mpt.imapmailbox.external.james.host;

import org.apache.james.util.Port;

import com.google.common.base.Preconditions;

public class ExternalJamesConfigurationEnvironnementVariables implements ExternalJamesConfiguration {

    private static final String ENV_JAMES_ADDRESS = "JAMES_ADDRESS";
    private static final String ENV_JAMES_IMAP_PORT = "JAMES_IMAP_PORT";
    private static final String ENV_JAMES_SMTP_PORT = "JAMES_SMTP_PORT";

    private final String address;
    private final Port imapPort;
    private final Port smtpPort;

    public ExternalJamesConfigurationEnvironnementVariables() {
        Preconditions.checkState(System.getenv(ENV_JAMES_ADDRESS) != null, "You must have exported an environment variable called JAMES_ADDRESS in order to run these tests. For instance export JAMES_ADDRESS=127.0.0.1");
        Preconditions.checkState(System.getenv(ENV_JAMES_IMAP_PORT) != null, "You must have exported an environment variable called JAMES_IMAP_PORT in order to run these tests. For instance export JAMES_IMAP_PORT=143");
        Preconditions.checkState(System.getenv(ENV_JAMES_SMTP_PORT) != null, "You must have exported an environment variable called JAMES_SMTP_PORT in order to run these tests. For instance export JAMES_IMAP_PORT=143");
        this.address = System.getenv(ENV_JAMES_ADDRESS);
        this.imapPort = Port.of(Integer.parseInt(System.getenv(ENV_JAMES_IMAP_PORT)));
        this.smtpPort = Port.of(Integer.parseInt(System.getenv(ENV_JAMES_SMTP_PORT)));
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Port getImapPort() {
        return imapPort;
    }

    @Override
    public Port getSmptPort() {
        return smtpPort;
    }
}
