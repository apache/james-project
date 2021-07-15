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

package org.apache.james.jmap.mail

import org.apache.james.core.Domain
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.mailbox.model.{QuotaRoot => ModelQuotaRoot}

import scala.compat.java8.OptionConverters._

object QuotaRoot{
  def toJmap(quotaRoot: ModelQuotaRoot): QuotaRoot = QuotaRoot(quotaRoot.getValue, quotaRoot.getDomain.asScala)
}

case class QuotaRoot(value: String, domain: Option[Domain]) {
  def toModel: ModelQuotaRoot = ModelQuotaRoot.quotaRoot(value, domain.asJava)
}

object Quotas {
  sealed trait Type
  case object Storage extends Type
  case object Message extends Type

  def from(quotas: Map[QuotaId, Quota]) = new Quotas(quotas)

  def from(quotaId: QuotaId, quota: Quota) = new Quotas(Map(quotaId -> quota))
}

object QuotaId {
  def fromQuotaRoot(quotaRoot: QuotaRoot): QuotaId = QuotaId(quotaRoot)
}

case class QuotaId(quotaRoot: QuotaRoot) extends AnyVal {
  def getName: String = quotaRoot.value
}

object Quota {
  def from(quota: Map[Quotas.Type, Value]) = new Quota(quota)
}

case class Quota(quota: Map[Quotas.Type, Value]) extends AnyVal

case class Value(used: UnsignedInt, max: Option[UnsignedInt])

case class Quotas(quotas: Map[QuotaId, Quota]) extends AnyVal