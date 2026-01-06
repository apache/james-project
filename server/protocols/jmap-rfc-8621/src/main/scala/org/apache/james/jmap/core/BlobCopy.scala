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

package org.apache.james.jmap.core

import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.{ValidableRequest, WithAccountId}

case class BlobCopyRequest(fromAccountId: AccountId,
                           accountId: AccountId,
                           blobIds: BlobIds) extends WithAccountId with ValidableRequest {
  override def validate(configuration: JmapRfc8621Configuration): Either[Exception, BlobCopyRequest] =
    if (blobIds.value.size > configuration.maxObjectsInSet.value.value) {
      Left(RequestTooLargeException(s"""Too many items in a Blob/copy request.
        Got ${blobIds.value.size} items instead of maximum ${configuration.maxObjectsInSet.value.value}."""))
    } else {
      scala.Right(this)
    }
}

case class BlobCopyResponse(fromAccountId: AccountId,
                            accountId: AccountId,
                            copied: Option[Map[BlobId, BlobId]],
                            notCopied: Option[Map[BlobId, SetError]])
