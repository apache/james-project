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

package org.apache.james.vault.metadata;

import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraDeletedMessageMetadataVaultTest implements DeletedMessageMetadataVaultContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private DeletedMessageMetadataVault testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        DeletedMessageWithStorageInformationConverter dtoConverter = new DeletedMessageWithStorageInformationConverter(blobIdFactory, messageIdFactory, new InMemoryId.Factory());

        testee = new CassandraDeletedMessageMetadataVault(
            new MetadataDAO(cassandra.getConf(), messageIdFactory, dtoConverter),
            new StorageInformationDAO(cassandra.getConf(), blobIdFactory),
            new UserPerBucketDAO(cassandra.getConf()));
    }

    @Override
    public DeletedMessageMetadataVault metadataVault() {
        return testee;
    }
}