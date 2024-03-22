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

package org.apache.james.jmap.utils.quotas

import jakarta.inject.Inject
import org.apache.james.core.quota.{QuotaLimitValue, QuotaUsageValue}
import org.apache.james.jmap.core.UnsignedInt
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.mail.{Quota, QuotaId, QuotaRoot, Quotas, Value}
import org.apache.james.mailbox.exception.MailboxException
import org.apache.james.mailbox.model.{Quota => ModelQuota}
import org.apache.james.mailbox.quota.QuotaManager
import reactor.core.scala.publisher.SMono

class QuotaReader @Inject() (quotaManager: QuotaManager) {
  @throws[MailboxException]
  def retrieveQuotas(quotaRoot: QuotaRoot): SMono[Quotas] =
    SMono(quotaManager.getQuotasReactive(quotaRoot.toModel))
      .map(quotas => Quotas.from(
        QuotaId.fromQuotaRoot(quotaRoot),
        Quota.from(Map(
          Quotas.Storage -> quotaToValue(quotas.getStorageQuota),
          Quotas.Message -> quotaToValue(quotas.getMessageQuota)))))

  private def quotaToValue[T <: QuotaLimitValue[T], U <: QuotaUsageValue[U, T]](quota: ModelQuota[T, U]): Value =
    Value(
      UnsignedInt.liftOrThrow(quota.getUsed.asLong),
      asNumber(quota.getLimit))

  private def asNumber(value: QuotaLimitValue[_]): Option[UnsignedInt] =
    if (value.isUnlimited) {
      None
    } else {
      Some(UnsignedInt.liftOrThrow(value.asLong))
    }
}
