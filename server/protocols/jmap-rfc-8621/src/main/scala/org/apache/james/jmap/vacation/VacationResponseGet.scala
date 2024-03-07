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

package org.apache.james.jmap.vacation

import org.apache.james.jmap.core.{AccountId, Properties, UuidState}
import org.apache.james.jmap.method.{GetRequest, WithAccountId}

case class VacationResponseIds(value: List[UnparsedVacationResponseId])

case class VacationResponseGetRequest(accountId: AccountId,
                                      ids: Option[VacationResponseIds],
                                      properties: Option[Properties]) extends WithAccountId with GetRequest {
  override def idCount: Option[Long] = ids.map(_.value).map(_.size)
}

case class VacationResponseNotFound(value: Set[UnparsedVacationResponseId]) {
  def merge(other: VacationResponseNotFound): VacationResponseNotFound = VacationResponseNotFound(this.value ++ other.value)
}

case class VacationResponseGetResponse(accountId: AccountId,
                                       state: UuidState,
                                       list: List[VacationResponse],
                                       notFound: VacationResponseNotFound)
