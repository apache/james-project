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

package org.apache.james.jmap.methods.integration.cucumber;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.probe.ACLProbe;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;

import com.google.common.base.Charsets;

import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class MainStepdefs {

    public GuiceJamesServer jmapServer;
    public DataProbe dataProbe;
    public MailboxProbe mailboxProbe;
    public ACLProbe aclProbe;
    public Runnable awaitMethod = () -> {};
    public MessageId.Factory messageIdFactory;
    
    public void init() throws Exception {
        jmapServer.start();
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);
    }
    

    public URIBuilder baseUri() {
        return new URIBuilder()
                .setScheme("http")
                .setHost("localhost")
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .setCharset(Charsets.UTF_8);
    }
    
    public void tearDown() {
        jmapServer.stop();
    }

}
