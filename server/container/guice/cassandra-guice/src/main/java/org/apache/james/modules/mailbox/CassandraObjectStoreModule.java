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

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ObjectStore;
import org.apache.james.blob.cassandra.CassandraBlobId;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class CassandraObjectStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraBlobsDAO.class).in(Scopes.SINGLETON);
        bind(CassandraBlobId.Factory.class).in(Scopes.SINGLETON);

        bind(ObjectStore.class).to(CassandraBlobsDAO.class);
        bind(BlobId.Factory.class).to(CassandraBlobId.Factory.class);
    }
}
