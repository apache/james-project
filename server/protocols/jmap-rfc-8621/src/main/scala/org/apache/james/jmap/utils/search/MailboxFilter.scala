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
import org.apache.james.jmap.mail.{EmailQueryRequest, HeaderContains, HeaderExist, UnsupportedFilterException}
import org.apache.james.jmap.model.CapabilityIdentifier
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.{AccessibleNamespace, Namespace, PersonalNamespace}
import org.apache.james.mailbox.model.SearchQuery.DateResolution.Second
import org.apache.james.mailbox.model.SearchQuery.{AddressType, DateComparator, DateOperator, DateResolution, InternalDateCriterion}
import org.apache.james.mailbox.model.{MultimailboxesSearchQuery, SearchQuery}

import scala.jdk.CollectionConverters._

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
    def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder]
  }

  object QueryFilter {
    def buildQuery(request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] = {
      List(ReceivedBefore, ReceivedAfter, HasAttachment, HasKeyWord, NotKeyWord, MinSize, MaxSize,
           AllInThreadHaveKeyword, NoneInThreadHaveKeyword, SomeInThreadHaveKeyword, Text, From,
           To, Cc, Bcc, Subject, Header, Body)
        .foldLeftM(new SearchQuery.Builder())((builder, filter) => filter.toQuery(builder, request))
    }
  }

  case object ReceivedBefore extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.before) match {
        case Some(before) =>
          val strictlyBefore = SearchQuery.internalDateBefore(Date.from(before.asUTC.toInstant), Second)
          val sameDate = SearchQuery.internalDateOn(Date.from(before.asUTC.toInstant), Second)
          Right(builder
            .andCriteria(SearchQuery.or(strictlyBefore, sameDate)))
        case None => Right(builder)
      }
  }

  case object ReceivedAfter extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.after) match {
        case Some(after) =>
          val strictlyAfter = new InternalDateCriterion(new DateOperator(DateComparator.AFTER, Date.from(after.asUTC.toInstant), DateResolution.Second))
          Right(builder
            .andCriteria(strictlyAfter))
        case None => Right(builder)
      }
  }

  case object HasAttachment extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.hasAttachment) match {
        case Some(hasAttachment) => Right(builder
          .andCriteria(SearchQuery.hasAttachment(hasAttachment.value)))
        case None => Right(builder)
      }
  }

  case object MinSize extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.minSize) match {
        case Some(minSize) =>
          Right(builder
            .andCriteria(SearchQuery.or(
              SearchQuery.sizeGreaterThan(minSize.value),
              SearchQuery.sizeEquals(minSize.value))))
        case None => Right(builder)
      }
  }

  case object MaxSize extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.maxSize) match {
        case Some(maxSize) =>
          Right(builder
            .andCriteria(SearchQuery.sizeLessThan(maxSize.value)))
        case None => Right(builder)
      }
  }

  case object HasKeyWord extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.hasKeyword) match {
        case Some(keyword) =>
          keyword.asSystemFlag match {
            case Some(systemFlag) => Right(builder.andCriteria(SearchQuery.flagIsSet(systemFlag)))
            case None => Right(builder.andCriteria(SearchQuery.flagIsSet(keyword.flagName)))
          }
        case None => Right(builder)
      }
  }
  case object NotKeyWord extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.notKeyword) match {
        case Some(keyword) =>
          keyword.asSystemFlag match {
            case Some(systemFlag) => Right(builder.andCriteria(SearchQuery.flagIsUnSet(systemFlag)))
            case None => Right(builder.andCriteria(SearchQuery.flagIsUnSet(keyword.flagName)))
          }
        case None => Right(builder)
      }
  }
  case object AllInThreadHaveKeyword extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.allInThreadHaveKeyword) match {
        case Some(_) => Left(UnsupportedFilterException("allInThreadHaveKeyword"))
        case None => Right(builder)
      }
  }
  case object NoneInThreadHaveKeyword extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.noneInThreadHaveKeyword) match {
        case Some(_) => Left(UnsupportedFilterException("noneInThreadHaveKeyword"))
        case None => Right(builder)
      }
  }
  case object SomeInThreadHaveKeyword extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.someInThreadHaveKeyword) match {
        case Some(_) => Left(UnsupportedFilterException("someInThreadHaveKeyword"))
        case None => Right(builder)
      }
  }
  case object Text extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.text) match {
        case Some(_) => Left(UnsupportedFilterException("text"))
        case None => Right(builder)
      }
  }
  case object From extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.from) match {
        case Some(from) => Right(builder.andCriteria(SearchQuery.address(AddressType.From, from.value)))
        case None => Right(builder)
      }
  }
  case object To extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.to) match {
        case Some(to) => Right(builder.andCriteria(SearchQuery.address(AddressType.To, to.value)))
        case None => Right(builder)
      }
  }
  case object Cc extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.cc) match {
        case Some(cc) => Right(builder.andCriteria(SearchQuery.address(AddressType.Cc, cc.value)))
        case None => Right(builder)
      }
  }
  case object Bcc extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.bcc) match {
        case Some(bcc) => Right(builder.andCriteria(SearchQuery.address(AddressType.Bcc, bcc.value)))
        case None => Right(builder)
      }
  }
  case object Subject extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.subject) match {
        case Some(subject) => Right(builder.andCriteria(SearchQuery.headerContains("Subject", subject.value)))
        case None => Right(builder)
      }
  }
  case object Header extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.header) match {
        case Some(HeaderExist(name)) => Right(builder.andCriteria(SearchQuery.headerExists(name)))
        case Some(HeaderContains(name, value)) => Right(builder.andCriteria(SearchQuery.headerContains(name, value)))
        case None => Right(builder)
      }
  }
  case object Body extends QueryFilter {
    override def toQuery(builder: SearchQuery.Builder, request: EmailQueryRequest): Either[UnsupportedFilterException, SearchQuery.Builder] =
      request.filter.flatMap(_.body) match {
        case Some(_) => Left(UnsupportedFilterException("body"))
        case None => Right(builder)
      }
  }
}
