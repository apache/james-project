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

import java.util.Date

import cats.implicits._
import org.apache.james.jmap.core.CapabilityIdentifier
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.mail.{And, EmailQueryRequest, FilterCondition, FilterOperator, FilterQuery, HeaderContains, HeaderExist, Not, Operator, Or, UnsupportedFilterException}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.{AccessibleNamespace, Namespace, PersonalNamespace}
import org.apache.james.mailbox.model.SearchQuery.DateResolution.Second
import org.apache.james.mailbox.model.SearchQuery.{AddressType, Criterion, DateComparator, DateOperator, DateResolution, InternalDateCriterion}
import org.apache.james.mailbox.model.{MultimailboxesSearchQuery, SearchQuery}

import scala.jdk.CollectionConverters._

sealed trait MailboxFilter {
  def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder
}

case object InMailboxFilter extends MailboxFilter {
  override def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder =
    request.filter.flatMap {
      case filterCondition: FilterCondition => Some(filterCondition)
      // Extract mailbox condition from simple AND
      case filterOperator: FilterOperator if filterOperator.operator == And &&
        filterOperator.countMailboxFilter == 1 =>
        filterOperator.conditions.flatMap {
          case filterCondition: FilterCondition if filterCondition.inMailbox.isDefined => Some(filterCondition)
          case _ => None
        }.lastOption
      case _ => None
    }.flatMap(_.inMailbox) match {
      case Some(mailboxId) => builder.inMailboxes(mailboxId)
      case None => builder
    }
}

case object NotInMailboxFilter extends MailboxFilter {
  override def toQuery(builder: MultimailboxesSearchQuery.Builder, request: EmailQueryRequest): MultimailboxesSearchQuery.Builder =
    request.filter.flatMap {
      case filterCondition: FilterCondition => Some(filterCondition)
      // Extract mailbox condition from simple AND
      case filterOperator: FilterOperator if filterOperator.operator == And &&
        filterOperator.countMailboxFilter == 1 =>
        filterOperator.conditions.flatMap {
          case filterCondition: FilterCondition if filterCondition.inMailboxOtherThan.isDefined => Some(filterCondition)
          case _ => None
        }.lastOption
      case _ => None
    }.flatMap(_.inMailboxOtherThan) match {
      case Some(mailboxIds) => builder.notInMailboxes(mailboxIds.asJava)
      case None => builder
  }
}

object MailboxFilter {
  def buildQuery(request: EmailQueryRequest, searchQuery: SearchQuery, capabilities: Set[CapabilityIdentifier], session: MailboxSession) = {
    val multiMailboxQueryBuilder = MultimailboxesSearchQuery.from(searchQuery)
        .inNamespace(queryNamespace(capabilities, session))

    List(InMailboxFilter, NotInMailboxFilter).foldLeft(multiMailboxQueryBuilder)((builder, filter) => filter.toQuery(builder, request))
      .build()
  }

  private def queryNamespace(capabilities: Set[CapabilityIdentifier], session: MailboxSession): Namespace = if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
    new AccessibleNamespace()
  } else {
    new PersonalNamespace(session)
  }

  sealed trait QueryFilter {
    def toQuery(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]]
  }

  sealed trait ConditionFilter extends QueryFilter {
    def toQuery(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] = filterQuery match {
      case filterCondition: FilterCondition => toQuery(filterCondition)
      case _ => Right(Nil)
    }

    def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]]
  }

  object OperatorQueryFilter {
    def toQuery(filterQuery: FilterQuery,
                converter: List[Criterion] => Criterion,
                operator: Operator): Either[UnsupportedFilterException, List[Criterion]] =
      filterQuery match {
        case filterOperator: FilterOperator if filterOperator.operator.equals(operator) =>
          filterOperator.conditions
            .map(QueryFilter.toCriterion)
            .toList
            .sequence
            .map(_.flatten)
            .map(criteria => List(converter.apply(criteria)))
        case _ => Right(Nil)
      }
  }

  case object AndFilter extends QueryFilter {
    override def toQuery(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] =
      OperatorQueryFilter.toQuery(filterQuery, criteria => SearchQuery.and(criteria.asJava), And)
  }

  case object OrFilter extends QueryFilter {
    override def toQuery(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] =
      OperatorQueryFilter.toQuery(filterQuery, criteria => SearchQuery.or(criteria.asJava), Or)
  }

  case object NotFilter extends QueryFilter {
    override def toQuery(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] =
      OperatorQueryFilter.toQuery(filterQuery, criteria => SearchQuery.not(criteria.asJava), Not)
  }

  object QueryFilter {
    def buildQuery(request: EmailQueryRequest): Either[UnsupportedOperationException, SearchQuery.Builder] =
      request.validatedFilter.flatMap(
        _.map(toCriterion)
          .getOrElse(Right(Nil)))
        .map(criteria => new SearchQuery.Builder().andCriteria(criteria.asJava))

    def toCriterion(filterQuery: FilterQuery): Either[UnsupportedFilterException, List[Criterion]] =
      List(ReceivedBefore, ReceivedAfter, HasAttachment, HasKeyWord, NotKeyWord, MinSize, MaxSize,
           AllInThreadHaveKeyword, NoneInThreadHaveKeyword, SomeInThreadHaveKeyword, Text, From,
           To, Cc, Bcc, Subject, Header, Body, AndFilter, OrFilter, NotFilter)
        .map(filter => filter.toQuery(filterQuery))
        .sequence
        .map(list => list.flatten)
  }

  case object ReceivedBefore extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.before match {
        case Some(before) =>
          val strictlyBefore = SearchQuery.internalDateBefore(Date.from(before.asUTC.toInstant), Second)
          val sameDate = SearchQuery.internalDateOn(Date.from(before.asUTC.toInstant), Second)
          Right(List(SearchQuery.or(strictlyBefore, sameDate)))
        case None => Right(Nil)
      }
  }

  case object ReceivedAfter extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.after match {
        case Some(after) =>
          val strictlyAfter = new InternalDateCriterion(new DateOperator(DateComparator.AFTER, Date.from(after.asUTC.toInstant), DateResolution.Second))
          Right(List(strictlyAfter))
        case None => Right(Nil)
      }
  }

  case object HasAttachment extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.hasAttachment match {
        case Some(hasAttachment) => Right(List(SearchQuery.hasAttachment(hasAttachment.value)))
        case None => Right(Nil)
      }
  }

  case object MinSize extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.minSize match {
        case Some(minSize) =>
          Right(List(SearchQuery.or(
              SearchQuery.sizeGreaterThan(minSize.value),
              SearchQuery.sizeEquals(minSize.value))))
        case None => Right(Nil)
      }
  }

  case object MaxSize extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.maxSize match {
        case Some(maxSize) =>
          Right(List(SearchQuery.sizeLessThan(maxSize.value)))
        case None => Right(Nil)
      }
  }

  case object HasKeyWord extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.hasKeyword match {
        case Some(keyword) =>
          keyword.asSystemFlag match {
            case Some(systemFlag) => Right(List(SearchQuery.flagIsSet(systemFlag)))
            case None => Right(List(SearchQuery.flagIsSet(keyword.flagName)))
          }
        case None => Right(Nil)
      }
  }
  case object NotKeyWord extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.notKeyword match {
        case Some(keyword) =>
          keyword.asSystemFlag match {
            case Some(systemFlag) => Right(List(SearchQuery.flagIsUnSet(systemFlag)))
            case None => Right(List(SearchQuery.flagIsUnSet(keyword.flagName)))
          }
        case None => Right(Nil)
      }
  }
  case object AllInThreadHaveKeyword extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.allInThreadHaveKeyword match {
        case Some(_) => Left(UnsupportedFilterException("allInThreadHaveKeyword"))
        case None => Right(Nil)
      }
  }
  case object NoneInThreadHaveKeyword extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.noneInThreadHaveKeyword match {
        case Some(_) => Left(UnsupportedFilterException("noneInThreadHaveKeyword"))
        case None => Right(Nil)
      }
  }
  case object SomeInThreadHaveKeyword extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.someInThreadHaveKeyword match {
        case Some(_) => Left(UnsupportedFilterException("someInThreadHaveKeyword"))
        case None => Right(Nil)
      }
  }

  case object Text extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.text match {
        case Some(text) =>
          Right(List(SearchQuery.or(
            List(SearchQuery.address(AddressType.To, text.value),
              SearchQuery.address(AddressType.Cc, text.value),
              SearchQuery.address(AddressType.Bcc, text.value),
              SearchQuery.address(AddressType.From, text.value),
              SearchQuery.subject(text.value),
              SearchQuery.bodyContains(text.value))
            .asJava)))
        case None => Right(Nil)
      }
  }

  case object From extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.from match {
        case Some(from) => Right(List(SearchQuery.address(AddressType.From, from.value)))
        case None => Right(Nil)
      }
  }
  case object To extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.to match {
        case Some(to) => Right(List(SearchQuery.address(AddressType.To, to.value)))
        case None => Right(Nil)
      }
  }
  case object Cc extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.cc match {
        case Some(cc) => Right(List(SearchQuery.address(AddressType.Cc, cc.value)))
        case None => Right(Nil)
      }
  }
  case object Bcc extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.bcc match {
        case Some(bcc) => Right(List(SearchQuery.address(AddressType.Bcc, bcc.value)))
        case None => Right(Nil)
      }
  }
  case object Subject extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.subject match {
        case Some(subject) => Right(List(SearchQuery.subject(subject.value)))
        case None => Right(Nil)
      }
  }
  case object Header extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.header match {
        case Some(HeaderExist(name)) => Right(List(SearchQuery.headerExists(name)))
        case Some(HeaderContains(name, value)) => Right(List(SearchQuery.headerContains(name, value)))
        case None => Right(Nil)
      }
  }

  case object Body extends ConditionFilter {
    override def toQuery(filterCondition: FilterCondition): Either[UnsupportedFilterException, List[Criterion]] =
      filterCondition.body match {
        case Some(text) => Right(List(SearchQuery.or(
          List(
            SearchQuery.attachmentContains(text.value),
            SearchQuery.bodyContains(text.value),
            SearchQuery.attachmentFileName(text.value))
          .asJava)))
        case None => Right(Nil)
      }
  }
}
