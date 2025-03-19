/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.cassandra.identity

import com.datastax.oss.driver.api.core.`type`.DataTypes.{BOOLEAN, INT, TEXT, UUID, frozenSetOf}
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable.{BCC, EMAIL, EMAIL_ADDRESS, EmailAddress, HTML_SIGNATURE, ID, MAY_DELETE, NAME, REPLY_TO, SORT_ORDER, TEXT_SIGNATURE, USER}

object CassandraCustomIdentityDataDefinition {
  val MODULE: CassandraDataDefinition = CassandraDataDefinition.builder()
    .`type`(EMAIL_ADDRESS.asCql(true))
    .statement(statement => statement
      .withField(EmailAddress.NAME, TEXT)
      .withField(EmailAddress.EMAIL, TEXT))

    .table(CassandraCustomIdentityTable.TABLE_NAME)
    .comment("Hold user custom identities data following JMAP RFC-8621 Identity concept")
    .statement(statement => types => statement
      .withPartitionKey(USER, TEXT)
      .withClusteringColumn(ID, UUID)
      .withColumn(NAME, TEXT)
      .withColumn(EMAIL, TEXT)
      .withColumn(REPLY_TO, frozenSetOf(types.getDefinedUserType(EMAIL_ADDRESS.asCql(true))))
      .withColumn(BCC, frozenSetOf(types.getDefinedUserType(EMAIL_ADDRESS.asCql(true))))
      .withColumn(TEXT_SIGNATURE, TEXT)
      .withColumn(HTML_SIGNATURE, TEXT)
      .withColumn(SORT_ORDER, INT)
      .withColumn(MAY_DELETE, BOOLEAN))
    .build()
}