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
package org.apache.james.jmap;

import java.io.FileNotFoundException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.methods.RequestHandler;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

public class JMAPModule extends AbstractModule {

    private static final int DEFAULT_PORT = 80;

    @Override
    protected void configure() {
        install(new JMAPCommonModule());
        bind(AuthenticationFilter.class);
        bind(RequestHandler.class).toInstance(new RequestHandler() {

            @Override
            public ProtocolResponse process(ProtocolRequest request) {
                // TODO Auto-generated method stub
                return null;
            }
            
        });

        bindConstant().annotatedWith(Names.named(JMAPServer.DEFAULT_JMAP_PORT)).to(DEFAULT_PORT);
    }

    @Provides
    @Singleton
    JMAPConfiguration provideConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException{
        PropertiesConfiguration configuration = getConfiguration(fileSystem);
        String keystore = configuration.getString("tls.keystoreURL");
        String secret = configuration.getString("tls.secret");
        return new JMAPConfiguration(keystore, secret);
    }

    private PropertiesConfiguration getConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        return new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "jmap.properties"));
    }
}
