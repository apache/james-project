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

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryUrlStore;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.data.JPADomainListModule;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.modules.data.JPARecipientRewriteTableModule;
import org.apache.james.modules.data.JPAUsersRepositoryModule;
import org.apache.james.modules.data.MemoryDelegationStoreModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.mailrepository.BlobstoreMailRepositoryModule;
import org.apache.james.modules.objectstorage.S3BlobStoreModule;
import org.apache.james.modules.objectstorage.S3BucketModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailetContainerModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminMailOverWebModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.queue.pulsar.module.PulsarQueueModule;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;

import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;

public class Main implements JamesServerMain {
    public static final Module WEBADMIN = Modules.combine(
        new DataRoutesModules(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new NoJwtModule(),
        new WebAdminServerModule(),
        new WebAdminMailOverWebModule());
    public static final Module PROTOCOLS = Modules.combine(
        new SMTPServerModule(),
        new ProtocolHandlerModule());
    private static final Module BLOB_MODULE = Modules.combine(
        new BlobStoreAPIModule(),
        new S3BlobStoreModule(),
        new S3BucketModule(),
        binder -> {
            binder.bind(BlobStoreDAO.class).to(S3BlobStoreDAO.class);
            binder.bind(BlobStore.class)
                .annotatedWith(Names.named(MetricableBlobStore.BLOB_STORE_IMPLEMENTATION))
                .to(PassThroughBlobStore.class);
        });


    public static final Module QUEUE_MODULES = Modules.combine(
        new RawPostDequeueDecoratorModule(),
        new PulsarQueueModule());

    public static final Module SERVER_CORE_MODULES = Modules.combine(
        new DefaultProcessorsConfigurationProviderModule(),
        new JPAEntityManagerModule(),
        new MailStoreRepositoryModule(),
        new MailetContainerModule(),
        new BlobstoreMailRepositoryModule(),
        binder -> binder.bind(MailRepositoryUrlStore.class).to(JPAMailRepositoryUrlStore.class).in(Scopes.SINGLETON),
        new CoreDataModule(),
        new JPADomainListModule(),
        new JPARecipientRewriteTableModule(),
        new JPAUsersRepositoryModule(),
        new MemoryDelegationStoreModule(),
        new TaskManagerModule());

    public static void main(String[] args) throws Exception {
        SMTPRelayConfiguration configuration = SMTPRelayConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .overrideWith(new RunArgumentsModule(args));

        try {
            JamesServerMain.main(server);
        } catch (Exception e) {
            LOGGER.error("Failed to start", e);
            throw e;
        }
    }

    public static GuiceJamesServer createServer(SMTPRelayConfiguration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(SERVER_CORE_MODULES, BLOB_MODULE, QUEUE_MODULES, PROTOCOLS, WEBADMIN);
    }
}
