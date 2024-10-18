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

package org.apache.james.jmap.highlight

import cats.implicits._
import com.google.common.base.Preconditions
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.highlight.SearchSnippetGetRequest.filterAsCriterionList
import org.apache.james.jmap.mail.{Email, FilterQuery, UnparsedEmailId, UnsupportedFilterException}
import org.apache.james.jmap.method.{GetRequest, WithAccountId}
import org.apache.james.jmap.utils.search.MailboxFilter.{AndFilter, Body, NotFilter, OrFilter, QueryFilter, Subject, Text}
import org.apache.james.mailbox.model.SearchQuery.Criterion
import org.apache.james.mailbox.model.{MessageId, SearchQuery}
import org.apache.james.mailbox.searchhighligt.{SearchSnippet => JavaSearchSnippet}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object SearchSnippetGetRequest {

  private val FILTER_SUPPORT_LIST: List[QueryFilter] = List(Text, Subject, Body, NotFilter, OrFilter, AndFilter)

  private def filterAsCriterionList(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] =
    FILTER_SUPPORT_LIST.traverse(_.toQuery(filterQuery)).map(_.flatten)
}

case class SearchSnippetGetRequest(accountId: AccountId,
                                   filter: Option[FilterQuery],
                                   emailIds: List[UnparsedEmailId]) extends WithAccountId with GetRequest {

  override def idCount: Option[Int] = Some(emailIds.size)

  def emptyRequest: Boolean = emailIds.isEmpty || filter.isEmpty

  def tryFilterAsSearchQuery: Either[UnsupportedOperationException, SearchQuery.Builder] = {
    Preconditions.checkArgument(filter.isDefined, "filter must be defined".asInstanceOf[Object])
    for {
      validatedFilter <- FilterQuery.validateFilter(filter.get)
      criteria <- filterAsCriterionList(validatedFilter)
    } yield new SearchQuery.Builder().andCriteria(criteria.asJava)
  }

}

object SearchSnippetGetResponse {

  def from(accountId: AccountId, searchSnippetList: List[JavaSearchSnippet], emailIdsInRequest: List[UnparsedEmailId]): SearchSnippetGetResponse = {
    val foundEmailIds: Seq[UnparsedEmailId] = searchSnippetList.map(searchSnippet => emailIdAsUnparsedEmailId(searchSnippet.messageId()))
    val notFound: List[UnparsedEmailId] = emailIdsInRequest.diff(foundEmailIds.toList)
    SearchSnippetGetResponse(accountId, searchSnippetList.map(SearchSnippet.from), notFound)
  }

  private def emailIdAsUnparsedEmailId(emailId: MessageId): UnparsedEmailId = Email.asUnparsed(emailId)
    .fold(e => {
      throw new IllegalArgumentException("messageId is not a valid UnparsedEmailId", e)
    }, id => id)
}

case class SearchSnippetGetResponse(accountId: AccountId,
                                    list: List[SearchSnippet],
                                    notFound: List[UnparsedEmailId])

object SearchSnippet {
  def from(searchSnippet: JavaSearchSnippet): SearchSnippet = SearchSnippet(
    emailId = searchSnippet.messageId(),
    subject = searchSnippet.highlightedSubject().toScala,
    preview = searchSnippet.highlightedBody().toScala)
}

case class SearchSnippet(emailId: MessageId,
                         subject: Option[String] = None,
                         preview: Option[String] = None)