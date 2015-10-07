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
package org.apache.james.imapserver.netty;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.slf4j.Logger;

public class IMAPServerFactory extends AbstractServerFactory {

    private FileSystem fileSystem;
    private ImapDecoder decoder;
    private ImapEncoder encoder;
    private ImapProcessor processor;
    
    @Inject
    public final void setFileSystem(@Named("filesystem") FileSystem filesystem) {
        this.fileSystem = filesystem;
    }

    @Inject
    public void setImapProcessor(@Named("imapProcessor") ImapProcessor processor) {
        this.processor = processor;
    }
    
    @Inject
    public void setImapDecoder(@Named("imapDecoder") ImapDecoder decoder) {
        this.decoder = decoder;
    }

    @Inject
    public void setImapEncoder(@Named("imapEncoder") ImapEncoder encoder) {
        this.encoder = encoder;
    }

    protected IMAPServer createServer() {
       return new IMAPServer();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(Logger log, HierarchicalConfiguration config) throws Exception {
        
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<AbstractConfigurableAsyncServer>();
        List<HierarchicalConfiguration> configs = config.configurationsAt("imapserver");
        
        for (HierarchicalConfiguration serverConfig: configs) {
            IMAPServer server = createServer();
            server.setLog(log);
            server.setFileSystem(fileSystem);
            server.setImapDecoder(decoder);
            server.setImapEncoder(encoder);
            server.setImapProcessor(processor);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
        
    }

}
