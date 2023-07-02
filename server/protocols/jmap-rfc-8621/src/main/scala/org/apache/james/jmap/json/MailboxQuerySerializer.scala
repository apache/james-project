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

package org.apache.james.jmap.json

import org.apache.james.jmap.core.{CanCalculateChanges, QueryState}
import org.apache.james.jmap.mail.{MailboxFilter, MailboxQueryRequest, MailboxQueryResponse, QueryChangesRequest}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json._

import scala.jdk.OptionConverters._
import scala.language.implicitConversions

object MailboxQuerySerializer {
  private implicit val canCalculateChangeWrites: Writes[CanCalculateChanges] = Json.valueWrites[CanCalculateChanges]

  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)

  private implicit val roleReads: Reads[Role] = {
    case JsString(value) => Role.from(value).toScala.map(JsSuccess(_))
      .getOrElse(JsError(s"$value is not a valid role"))
    case _ => JsError("Expecting a JsString to be representing a role")
  }

  private implicit val filterReads: Reads[MailboxFilter] =  {
    case jsObject: JsObject =>
      val unsupported: collection.Set[String] = jsObject.keys.diff(MailboxFilter.SUPPORTED)
      if (unsupported.nonEmpty) {
        JsError(s"These '${unsupported.mkString("[", ", ", "]")}' was unsupported filter options")
      } else {
        Json.reads[MailboxFilter].reads(jsObject)
      }
    case jsValue: JsValue => Json.reads[MailboxFilter].reads(jsValue)
  }

  private implicit val emailQueryRequestReads: Reads[MailboxQueryRequest] = Json.reads[MailboxQueryRequest]
  private implicit val queryStateWrites: Writes[QueryState] = Json.valueWrites[QueryState]
  private implicit val queryChangesReads: Reads[QueryChangesRequest] = Json.reads[QueryChangesRequest]

  private implicit val mailboxQueryResponseWrites: OWrites[MailboxQueryResponse] = Json.writes[MailboxQueryResponse]

  def serialize(mailboxQueryResponse: MailboxQueryResponse): JsObject = Json.toJsObject(mailboxQueryResponse)

  def deserialize(input: JsValue): JsResult[MailboxQueryRequest] = Json.fromJson[MailboxQueryRequest](input)
  def deserializeQueryChanges(input: JsValue): JsResult[QueryChangesRequest] = Json.fromJson[QueryChangesRequest](input)
}