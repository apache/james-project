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
package org.apache.james.jmap.utils.search

import org.apache.james.jmap.mail.EmailQueryRequest
import org.apache.james.mailbox.model.{MultimailboxesSearchQuery, SearchQuery}

import scala.collection.JavaConverters.seqAsJavaListConverter


sealed trait MailboxFilter {
  def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder
}

case object InMailboxFilter extends MailboxFilter {
  override def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder = request.filter.flatMap(_.inMailbox) match {
    case Some(mailboxId) => builder.inMailboxes(mailboxId)
    case None => builder
  }
}

case object NotInMailboxFilter extends MailboxFilter {
  override def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder = request.filter.flatMap(_.inMailboxOtherThan) match {
    case Some(mailboxIds) => builder.notInMailboxes(mailboxIds.asJava)
    case None => builder
  }
}

object MailboxFilter {
  def buildQuery(request: EmailQueryRequest, searchQuery: SearchQuery) = {
    val multiMailboxQueryBuilder = MultimailboxesSearchQuery.from(searchQuery)

    List(InMailboxFilter, NotInMailboxFilter).foldLeft(multiMailboxQueryBuilder)((builder, filter) => filter.toQuery(builder, request))
      .build()
  }
}
