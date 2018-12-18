/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.event.json

import org.apache.james.core.quota.QuotaValue
import org.apache.james.mailbox.acl.{ACLDiff => JavaACLDiff}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath => JavaMailboxPath, Quota => JavaQuota}

import scala.collection.JavaConverters._

object DTOs {
  object ACLDiff {
    def fromJava(javaACLDiff: JavaACLDiff): ACLDiff = ACLDiff(
      javaACLDiff.getOldACL.getEntries.asScala.toMap,
      javaACLDiff.getNewACL.getEntries.asScala.toMap)
  }

  object MailboxPath {
    def fromJava(javaMailboxPath: JavaMailboxPath): MailboxPath = MailboxPath(
      Option(javaMailboxPath.getNamespace),
      Option(javaMailboxPath.getUser),
      javaMailboxPath.getName)
  }

  object Quota {
    def toScala[T <: QuotaValue[T]](java: JavaQuota[T]): Quota[T] = Quota(
      used = java.getUsed,
      limit = java.getLimit,
      limits = java.getLimitByScope.asScala.toMap)
  }

  case class ACLDiff(oldACL: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights],
                     newACL: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights]) {
    def toJava: JavaACLDiff = new JavaACLDiff(new MailboxACL(oldACL.asJava), new MailboxACL(newACL.asJava))
  }

  case class MailboxPath(namespace: Option[String], user: Option[String], name: String) {
    def toJava: JavaMailboxPath = new JavaMailboxPath(namespace.orNull, user.orNull, name)
  }

  case class Quota[T <: QuotaValue[T]](used: T, limit: T, limits: Map[JavaQuota.Scope, T]) {
    def toJava: JavaQuota[T] =
      JavaQuota.builder[T]
        .used(used)
        .computedLimit(limit)
        .limitsByScope(limits.asJava)
        .build()
  }
}
