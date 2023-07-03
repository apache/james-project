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

package org.apache.james.jmap.mail

import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class MailboxQueryChangesRequest(accountId: AccountId,
                                      sinceQueryState: UuidState,
                                      maxChanges: Option[Limit],
                                      upToId: Option[AccountId],
                                      calculateTotal: Boolean) extends WithAccountId

case class MailboxQueryChangesResponse(accountId: AccountId,
                                       oldQueryState: UuidState,
                                       newQueryState: UuidState,
                                       total: Limit,
                                       removed: List[AccountId],
                                       added: List[AddedItem])

case class Filter(operator: String,
                  conditions: List[Condition])

case class Condition(hasKeyword: String)

case class AddedItem(id: AccountId,
                     index: Limit)