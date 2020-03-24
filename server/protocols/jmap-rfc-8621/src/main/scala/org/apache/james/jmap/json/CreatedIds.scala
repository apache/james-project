/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.json

import org.apache.james.jmap.model.CreatedIds
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import play.api.libs.json._

  object CreatedIds {
    implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]
    implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]
    implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

    implicit def createdIdsIdWrites(implicit serverIdWriter: Writes[ServerId]): Writes[Map[ClientId, ServerId]] =
      (ids: Map[ClientId, ServerId]) => {
        JsObject(ids.map {
          case (clientId, serverId) => (clientId.value.value, serverIdWriter.writes(serverId))
        }.toSeq)
      }

    implicit def createdIdsIdRead(implicit serverIdReader: Reads[ServerId]): Reads[Map[ClientId, ServerId]] =
      Reads.mapReads[ClientId, ServerId] {
        clientIdString => Json.fromJson[ClientId](JsString(clientIdString))
      }
  }