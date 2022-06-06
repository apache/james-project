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

package org.apache.james.jmap.cassandra.utils

import com.datastax.oss.driver.api.core.data.UdtValue
import org.apache.james.backends.cassandra.init.CassandraTypesProvider
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable

case class EmailAddressTupleUtil(typesProvider: CassandraTypesProvider) {
  val emailAddressType = typesProvider.getDefinedUserType(CassandraCustomIdentityTable.EMAIL_ADDRESS)

  def createEmailAddressUDT(name: Option[String], email: String): UdtValue = {
    val value = emailAddressType
      .newValue()
      .setString(CassandraCustomIdentityTable.EmailAddress.EMAIL, email)

    name.map(name => value.setString(CassandraCustomIdentityTable.EmailAddress.NAME, name))

    value
  }
}