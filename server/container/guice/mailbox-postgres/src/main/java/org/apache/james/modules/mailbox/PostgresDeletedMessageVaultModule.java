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

import static org.apache.james.mailbox.postgres.DeleteMessageListener.CONTENT_DELETION;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.events.EventListener;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.vault.DeletedMessageVaultDeletionListener;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition;
import org.apache.james.vault.metadata.PostgresDeletedMessageMetadataVault;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class PostgresDeletedMessageVaultModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DeletedMessageVaultModule());

        Multibinder<PostgresDataDefinition> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresDataDefinition.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresDeletedMessageMetadataDataDefinition.MODULE);

        bind(PostgresDeletedMessageMetadataVault.class).in(Scopes.SINGLETON);
        bind(DeletedMessageMetadataVault.class)
            .to(PostgresDeletedMessageMetadataVault.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(CONTENT_DELETION))
            .addBinding()
            .to(DeletedMessageVaultDeletionListener.class);
    }
}
