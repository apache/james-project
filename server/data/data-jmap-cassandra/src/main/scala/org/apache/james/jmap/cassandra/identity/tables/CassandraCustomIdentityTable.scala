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

object CassandraCustomIdentityTable {
  val TABLE_NAME: String = "custom_identity"
  val USER: String = "user"
  val ID: String = "id"
  val NAME: String = "name"
  val EMAIL: String = "email"
  val REPLY_TO: String = "reply_to"
  val BCC: String = "bcc"
  val TEXT_SIGNATURE: String = "text_signature"
  val HTML_SIGNATURE: String = "html_signature"
  val MAY_DELETE: String = "may_delete"
  val EMAIL_ADDRESS: String = "email_address"

  object EmailAddress {
    val NAME: String = "name"
    val EMAIL: String = "email"
  }
}