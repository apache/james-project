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

import org.apache.james.jmap.model.{AccountId, CanCalculateChange, Keyword, Limit, Position, QueryState, UTCDate}
import org.apache.james.mailbox.model.{MailboxId, MessageId}

case class FilterCondition(inMailbox: Option[MailboxId],
                           inMailboxOtherThan: Option[Seq[MailboxId]],
                           before: Option[UTCDate],
                           after: Option[UTCDate],
                           hasKeyword: Option[Keyword])

case class EmailQueryRequest(accountId: AccountId, filter: Option[FilterCondition])

case class EmailQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: CanCalculateChange,
                              ids: Seq[MessageId],
                              position: Position,
                              limit: Option[Limit])
