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
package org.apache.james.mpt.smtp;

import org.apache.james.mpt.smtp.host.CassandraJamesSmtpHostSystem;

import com.google.inject.AbstractModule;

public class SmtpTestModule extends AbstractModule {

    enum Port {
        SMTP(1025),
        SMTP_START_TTLS(1587);
        
        private int port;

        Port(int port) {
            this.port = port;
        }
    }
    
    private Port smtpPort;

    public SmtpTestModule(Port smtpPort) {
        this.smtpPort = smtpPort;
    }
    
    @Override
    protected void configure() {
        bind(SmtpHostSystem.class).toInstance(new CassandraJamesSmtpHostSystem(smtpPort.port));
    }

}
