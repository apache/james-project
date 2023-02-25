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

package org.apache.james.transport.mailets

import eu.timepit.refined.auto._
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AllowedQuantity, Increment, Rule, Rules}
import org.apache.james.transport.mailets.ConfigurationOps.{OptionOps, SizeOps}
import org.apache.james.util.DurationParser
import org.apache.mailet.{Mail, MailetConfig}

import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.util.Try

case class KeyPrefix(value: String)

object ConfigurationOps {

  implicit class OptionOps(mailetConfig: MailetConfig) {
    def getOptionalString(key: String): Option[String] = Option(mailetConfig.getInitParameter(key))

    def getOptionalLong(key: String): Option[Long] = Option(mailetConfig.getInitParameter(key)).map(_.toLong)
  }

  implicit class DurationOps(mailetConfig: MailetConfig) {
    def getDuration(key: String): Option[Duration] = mailetConfig.getOptionalString(key)
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
  }

  implicit class SizeOps(mailetConfig: MailetConfig) {
    def getOptionalSize(key: String): Option[org.apache.james.util.Size] = mailetConfig.getOptionalString(key)
      .map {
        case "" => throw new IllegalArgumentException(s"'$key' field cannot be empty if specified")
        case s  => s
      }
      .map(org.apache.james.util.Size.parse)
  }
}

object EntityType {

  implicit class EitherOps[E <: Throwable, A](either: Either[E, A]) {
    def orThrow(message: String): A = either.left.map(cause => new IllegalArgumentException(message, cause)).toTry.get
  }

  def extractRules(entityType: EntityType, duration: Duration, mailetConfig: MailetConfig): Option[Rules] = (entityType match {
    case Count      => mailetConfig.getOptionalLong("count")
    case Recipients => mailetConfig.getOptionalLong("recipients")
    case Size       => mailetConfig.getOptionalSize("size").map(_.asBytes())
    case TotalSize      => mailetConfig.getOptionalSize("totalSize").map(_.asBytes())
  }).map(AllowedQuantity.validate(_).orThrow(s"invalid quantity for ${entityType.asString}"))
    .map(quantity => Rules(Seq(Rule(quantity, duration))))

  def extractQuantity(entityType: EntityType, mail: Mail): Option[Increment] = entityType match {
    case Count      => Some(1)
    case Recipients =>
      Some(Increment
        .validate(mail.getRecipients.size())
        .orThrow(s"invalid quantity for ${entityType.asString}"))
    case Size       =>
      Some(Increment
        .validate(mail.getMessageSize.toInt)
        .orThrow(s"invalid quantity for ${entityType.asString}"))
    case TotalSize      =>
      Try(Math.multiplyExact(mail.getMessageSize.toInt, mail.getRecipients.size()))
        .map(Increment.validate(_).orThrow(s"invalid quantity for ${entityType.asString}"))
        .toOption
  }
}

sealed trait EntityType {
  def asString: String
}

case object Count extends EntityType {
  override val asString: String = "count"
}

case object Recipients extends EntityType {
  override val asString: String = "recipients"
}

case object Size extends EntityType {
  override val asString: String = "size"
}

case object TotalSize extends EntityType {
  override val asString: String = "totalSize"
}