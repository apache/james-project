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

package org.apache.james.modules.mailbox;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.blob.BlobStoreDeletedMessageVault;
import org.apache.james.vault.blob.BucketNameGenerator;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.apache.james.vault.metadata.CassandraDeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageMetadataModule;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageVaultDeletionCallback;
import org.apache.james.vault.metadata.MetadataDAO;
import org.apache.james.vault.metadata.StorageInformationDAO;
import org.apache.james.vault.metadata.UserPerBucketDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraDeletedMessageVaultModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DeletedMessageVaultModule());

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions
            .addBinding()
            .toInstance(DeletedMessageMetadataModule.MODULE);

        bind(MetadataDAO.class).in(Scopes.SINGLETON);
        bind(StorageInformationDAO.class).in(Scopes.SINGLETON);
        bind(UserPerBucketDAO.class).in(Scopes.SINGLETON);
        bind(DeletedMessageWithStorageInformationConverter.class).in(Scopes.SINGLETON);

        bind(CassandraDeletedMessageMetadataVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageMetadataVault.class)
            .to(CassandraDeletedMessageMetadataVault.class);

        bind(BucketNameGenerator.class).in(Scopes.SINGLETON);
        bind(BlobStoreDeletedMessageVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageVault.class)
            .to(BlobStoreDeletedMessageVault.class);

        Multibinder.newSetBinder(binder(), DeleteMessageListener.DeletionCallback.class)
            .addBinding()
            .to(DeletedMessageVaultDeletionCallback.class);
    }
}
