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

package org.apache.james.jmap.cassandra.identity.tables

import com.datastax.oss.driver.api.core.CqlIdentifier

object CassandraCustomIdentityTable {
  val TABLE_NAME: String = "custom_identity"

  val USER: CqlIdentifier = CqlIdentifier.fromCql("user")
  val ID: CqlIdentifier = CqlIdentifier.fromCql("id")
  val NAME: CqlIdentifier = CqlIdentifier.fromCql("name")
  val EMAIL: CqlIdentifier = CqlIdentifier.fromCql("email")
  val REPLY_TO: CqlIdentifier = CqlIdentifier.fromCql("reply_to")
  val BCC: CqlIdentifier = CqlIdentifier.fromCql("bcc")
  val TEXT_SIGNATURE: CqlIdentifier = CqlIdentifier.fromCql("text_signature")
  val HTML_SIGNATURE: CqlIdentifier = CqlIdentifier.fromCql("html_signature")
  val MAY_DELETE: CqlIdentifier = CqlIdentifier.fromCql("may_delete")
  val EMAIL_ADDRESS: CqlIdentifier = CqlIdentifier.fromCql("email_address")
  val SORT_ORDER: CqlIdentifier = CqlIdentifier.fromCql("sort_order")

  object EmailAddress {
    val NAME: CqlIdentifier = CqlIdentifier.fromCql("name")
    val EMAIL: CqlIdentifier = CqlIdentifier.fromCql("email")
  }
}