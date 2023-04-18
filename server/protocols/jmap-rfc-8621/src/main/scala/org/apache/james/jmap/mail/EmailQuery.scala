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

import cats.implicits._
import com.google.common.primitives.Booleans
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core.{AccountId, CanCalculateChanges, LimitUnparsed, PositionUnparsed, QueryState, UTCDate}
import org.apache.james.jmap.mail.IsAscending.ASCENDING
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.SearchQuery.Sort.Order.{NATURAL, REVERSE}
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause
import org.apache.james.mailbox.model.{MailboxId, MessageId, SearchQuery}

case class UnsupportedSortException(unsupportedSort: String) extends UnsupportedOperationException
case class UnsupportedFilterException(unsupportedFilter: String) extends UnsupportedOperationException
case class UnsupportedNestingException(message: String) extends UnsupportedOperationException
case class UnsupportedRequestParameterException(unsupportedParam: String) extends UnsupportedOperationException

sealed trait FilterQuery {
  def inMailboxFilterOnly: Boolean

  def inMailboxAndAfterFilterOnly: Boolean

  def inMailboxAndBeforeFilterOnly: Boolean

  def countNestedMailboxFilter: Int

  def countMailboxFilter: Int
}

sealed trait Operator
case object And extends Operator
case object Or extends Operator
case object Not extends Operator

case class FilterOperator(operator: Operator,
                          conditions: Seq[FilterQuery]) extends FilterQuery {
  override val inMailboxFilterOnly: Boolean = false

  override val inMailboxAndAfterFilterOnly: Boolean = false

  override val inMailboxAndBeforeFilterOnly: Boolean = false

  override def countNestedMailboxFilter: Int = conditions.map(_.countNestedMailboxFilter).sum

  override def countMailboxFilter: Int = conditions.map {
    case condition: FilterCondition => condition.countMailboxFilter
    case _ => 0
  }.sum
}

case class Text(value: String) extends AnyVal
case class From(value: String) extends AnyVal
case class To(value: String) extends AnyVal
case class Cc(value: String) extends AnyVal
case class Bcc(value: String) extends AnyVal
case class Body(value: String) extends AnyVal
sealed trait Header
case class HeaderExist(name: String) extends Header
case class HeaderContains(name: String, value: String) extends Header

object FilterCondition {
  val SUPPORTED: Set[String] = Set("inMailbox", "inMailboxOtherThan", "before", "after",
                                  "hasKeyword", "notKeyword", "minSize", "maxSize",
                                  "hasAttachment", "allInThreadHaveKeyword", "someInThreadHaveKeyword",
                                  "noneInThreadHaveKeyword", "text", "from", "to",
                                  "cc", "bcc", "subject", "header", "body")
}
case class FilterCondition(inMailbox: Option[MailboxId],
                           inMailboxOtherThan: Option[Seq[MailboxId]],
                           before: Option[UTCDate],
                           after: Option[UTCDate],
                           hasKeyword: Option[Keyword],
                           notKeyword: Option[Keyword],
                           minSize: Option[Size],
                           maxSize: Option[Size],
                           hasAttachment: Option[HasAttachment],
                           allInThreadHaveKeyword: Option[Keyword],
                           someInThreadHaveKeyword: Option[Keyword],
                           noneInThreadHaveKeyword: Option[Keyword],
                           text: Option[Text],
                           from: Option[From],
                           to: Option[To],
                           cc: Option[Cc],
                           bcc: Option[Bcc],
                           subject: Option[Subject],
                           header: Option[Header],
                           body: Option[Body]) extends FilterQuery {

  private val emailQueryViewInvalidCriteria: Boolean = hasKeyword.isEmpty &&
    notKeyword.isEmpty &&
    minSize.isEmpty &&
    maxSize.isEmpty &&
    hasAttachment.isEmpty &&
    allInThreadHaveKeyword.isEmpty &&
    someInThreadHaveKeyword.isEmpty &&
    noneInThreadHaveKeyword.isEmpty &&
    text.isEmpty &&
    from.isEmpty &&
    to.isEmpty &&
    cc.isEmpty &&
    bcc.isEmpty &&
    subject.isEmpty &&
    header.isEmpty &&
    body.isEmpty

  private val noOtherFiltersThanInMailboxAndAfter: Boolean = inMailboxOtherThan.isEmpty &&
    before.isEmpty &&
    emailQueryViewInvalidCriteria

  private val noOtherFiltersThanInMailboxAndBefore: Boolean = inMailboxOtherThan.isEmpty &&
    after.isEmpty &&
    emailQueryViewInvalidCriteria

  override val inMailboxFilterOnly: Boolean = inMailbox.nonEmpty &&
    after.isEmpty &&
    noOtherFiltersThanInMailboxAndAfter

  override val inMailboxAndAfterFilterOnly: Boolean = inMailbox.nonEmpty &&
    after.nonEmpty &&
    noOtherFiltersThanInMailboxAndAfter

  override val inMailboxAndBeforeFilterOnly: Boolean = inMailbox.nonEmpty &&
    before.nonEmpty &&
    noOtherFiltersThanInMailboxAndBefore

  override def countNestedMailboxFilter: Int = countMailboxFilter

  override def countMailboxFilter: Int = Booleans.countTrue(inMailbox.isDefined, inMailboxOtherThan.isDefined)
}

case class EmailQueryRequest(accountId: AccountId,
                             position: Option[PositionUnparsed],
                             limit: Option[LimitUnparsed],
                             filter: Option[FilterQuery],
                             sort: Option[Set[Comparator]],
                             collapseThreads: Option[CollapseThreads],
                             anchor: Option[Anchor],
                             anchorOffset: Option[AnchorOffset]) extends WithAccountId {
  val validatedFilter: Either[UnsupportedNestingException, Option[FilterQuery]] =
    filter.map(validateFilter)
      .sequence
      .map(_.flatten)

  private def validateFilter(filter: FilterQuery): Either[UnsupportedNestingException, Option[FilterQuery]] = filter match {
    case filterCondition: FilterCondition => scala.Right(Some(filterCondition))
    case filterOperator: FilterOperator if filterOperator.operator == And =>
      val nestedCount = filter.countNestedMailboxFilter
      val topLevelCount = filter.countMailboxFilter

      if (nestedCount == 1 && topLevelCount == 1)  {
        scala.Right(Some(filter))
      } else if (nestedCount == 0)  {
        scala.Right(Some(filter))
      } else {
        rejectMailboxFilters(filterOperator)
      }
    case operator: FilterOperator => rejectMailboxFilters(operator)
  }

  private def rejectMailboxFilters(filter: FilterQuery): Either[UnsupportedNestingException, Option[FilterQuery]] =
    filter match {
      case filterCondition: FilterCondition if filterCondition.inMailbox.isDefined =>
        scala.Left(UnsupportedNestingException("Nested inMailbox filters are not supported"))
      case filterCondition: FilterCondition if filterCondition.inMailboxOtherThan.isDefined =>
        scala.Left(UnsupportedNestingException("Nested inMailboxOtherThan filter are not supported"))
      case filterCondition: FilterCondition => scala.Right(Some(filterCondition))
      case filterOperator: FilterOperator => filterOperator.conditions
        .toList
        .map(rejectMailboxFilters)
        .sequence
        .map(_ => Some(filterOperator))
    }
}

sealed trait SortProperty {
  def toSortClause: Either[UnsupportedSortException, SortClause]
}
case object ReceivedAtSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.Arrival)
}
case object AllInThreadHaveKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("allInThreadHaveKeyword"))
}
case object SomeInThreadHaveKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("someInThreadHaveKeyword"))
}
case object SizeSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.Size)
}
case object FromSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.MailboxFrom)
}
case object ToSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.MailboxTo)
}
case object SubjectSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.BaseSubject)
}
case object HasKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("hasKeyword"))
}
case object SentAtSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.SentDate)
}

object IsAscending {
  val DESCENDING: IsAscending = IsAscending(false)
  val ASCENDING: IsAscending = IsAscending(true)
}
case class IsAscending(sortByASC: Boolean) extends AnyVal {
  def toSortOrder: SearchQuery.Sort.Order = if (sortByASC) {
    NATURAL
  } else {
    REVERSE
  }
}

case class Collation(value: String) extends AnyVal

object Comparator {
  val SENT_AT_DESC: Comparator = Comparator(SentAtSortProperty, Some(IsAscending.DESCENDING), None)
  val RECEIVED_AT_DESC: Comparator = Comparator(ReceivedAtSortProperty, Some(IsAscending.DESCENDING), None)
}

case class Comparator(property: SortProperty,
                      isAscending: Option[IsAscending],
                      collation: Option[Collation]) {
  def toSort: Either[UnsupportedSortException, SearchQuery.Sort] =
    for {
      sortClause <- property.toSortClause
    } yield new SearchQuery.Sort(sortClause, isAscending.getOrElse(ASCENDING).toSortOrder)
}

case class CollapseThreads(value: Boolean) extends AnyVal
case class Anchor(value: String) extends AnyVal
case class AnchorOffset(value: Int) extends AnyVal

case class EmailQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: CanCalculateChanges,
                              ids: Seq[MessageId],
                              position: Position,
                              limit: Option[Limit])
