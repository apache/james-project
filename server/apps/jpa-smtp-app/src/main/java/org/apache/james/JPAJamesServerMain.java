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

import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.data.JPAAuthorizatorModule;
import org.apache.james.modules.data.JPADataModule;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.modules.data.JPAUsersRepositoryModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.activemq.ActiveMQQueueModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;

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
        new NaiveDelegationStoreModule(),
        new MailetProcessingModule(),
        new JPAEntityManagerModule(),
        new JPADataModule(),
        new ActiveMQQueueModule(),
        new RawPostDequeueDecoratorModule(),
        new JPAAuthorizatorModule());

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        JPAJamesConfiguration configuration = JPAJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .overrideWith(new RunArgumentsModule(args));

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(JPAJamesConfiguration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(JPA_SERVER_MODULE, PROTOCOLS, new DKIMMailetModule())
            .combineWith(new UsersRepositoryModuleChooser(new JPAUsersRepositoryModule())
                .chooseModules(configuration.getUsersRepositoryImplementation()));
    }

}
