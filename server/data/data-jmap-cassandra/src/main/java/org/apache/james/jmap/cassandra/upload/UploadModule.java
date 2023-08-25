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

package org.apache.james.jmap.cassandra.upload;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataTypes;

public interface UploadModule {

    String TABLE_NAME = "uploadsV2";

    CqlIdentifier ID = CqlIdentifier.fromCql("id");
    CqlIdentifier CONTENT_TYPE = CqlIdentifier.fromCql("content_type");
    CqlIdentifier SIZE = CqlIdentifier.fromCql("size");
    CqlIdentifier BLOB_ID = CqlIdentifier.fromCql("blob_id");
    CqlIdentifier USER = CqlIdentifier.fromCql("user");
    CqlIdentifier UPLOAD_DATE = CqlIdentifier.fromCql("upload_date");

    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Holds JMAP uploads")
        .statement(statement -> types -> statement
            .withPartitionKey(USER, DataTypes.TEXT)
            .withClusteringColumn(ID, DataTypes.TIMEUUID)
            .withColumn(CONTENT_TYPE, DataTypes.TEXT)
            .withColumn(SIZE, DataTypes.BIGINT)
            .withColumn(BLOB_ID, DataTypes.TEXT)
            .withColumn(UPLOAD_DATE, DataTypes.TIMESTAMP))
        .build();
}
