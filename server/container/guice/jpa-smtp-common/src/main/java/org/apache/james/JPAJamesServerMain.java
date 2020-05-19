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

package org.apache.james;

import org.apache.james.modules.activemq.ActiveMQQueueModule;
import org.apache.james.modules.data.JPADataModule;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.ElasticSearchMetricReporterModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class JPAJamesServerMain implements JamesServerMain {

    private static final Module PROTOCOLS = Modules.combine(
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new MailRepositoriesRoutesModule(),
        new MailQueueRoutesModule(),
        new NoJwtModule(),
        new DefaultProcessorsConfigurationProviderModule(),
        new TaskManagerModule());
    
    private static final Module JPA_SERVER_MODULE = Modules.combine(
        new JPAEntityManagerModule(),
        new JPADataModule(),
        new ActiveMQQueueModule(),
        new RawPostDequeueDecoratorModule(),
        new ElasticSearchMetricReporterModule());

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        GuiceJamesServer server = createServer(configuration);

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(Configuration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(JPA_SERVER_MODULE,  PROTOCOLS, new DKIMMailetModule());
    }

}
