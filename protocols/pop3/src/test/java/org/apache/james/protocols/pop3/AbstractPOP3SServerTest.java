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
package org.apache.james.protocols.pop3;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3SClient;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.netty.Encryption;

public abstract class AbstractPOP3SServerTest extends AbstractPOP3ServerTest {


    @Override
    protected POP3Client createClient() {
        POP3SClient client = new POP3SClient(true, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }
    
    @Override
    protected ProtocolServer createServer(Protocol protocol) {
        return createEncryptedServer(protocol, Encryption.createTls(BogusSslContextFactory.getServerContext()));
    }
    
    protected abstract ProtocolServer createEncryptedServer(Protocol protocol, Encryption enc);

}
