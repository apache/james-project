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
import org.apache.james.jmap.mail.{QuotaRoot, Quotas}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.{MailboxPath, QuotaRoot => ModelQuotaRoot}
import org.apache.james.mailbox.quota.UserQuotaRootResolver
import reactor.core.scala.publisher.SMono


class QuotaLoaderWithPreloadedDefaultFactory @Inject()(quotaRootResolver: UserQuotaRootResolver, quotaReader: QuotaReader) {

  def loadFor(session: MailboxSession): SMono[QuotaLoaderWithPreloadedDefault] =
    getUserDefaultQuotas(session)
      .map(qotas => new QuotaLoaderWithPreloadedDefault(
        quotaRootResolver,
        quotaReader,
        session,
        qotas))

  private def getUserDefaultQuotas(session:MailboxSession): SMono[Quotas] = {
    val quotaRoot: ModelQuotaRoot = quotaRootResolver.forUser(session.getUser)
    quotaReader.retrieveQuotas(QuotaRoot.toJmap(quotaRoot))
  }
}

class QuotaLoaderWithPreloadedDefault(quotaRootResolver: UserQuotaRootResolver,
                                      quotaReader: QuotaReader,
                                      session: MailboxSession,
                                      preloadedUserDefaultQuotas: Quotas) extends QuotaLoader {
  override def getQuotas(mailboxPath: MailboxPath): SMono[Quotas] =
    if (mailboxPath.belongsTo(session)) {
      SMono.just(preloadedUserDefaultQuotas)
    } else {
      val quotaRoot: ModelQuotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath)
      quotaReader.retrieveQuotas(QuotaRoot.toJmap(quotaRoot))
    }
}
