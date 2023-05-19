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

package org.apache.james.jmap.memory.identity

import com.google.common.collect.{HashBasedTable, Table}
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.{CustomIdentityDAO, IdentityCreationRequest, IdentityNotFoundException, IdentityUpdate}
import org.apache.james.jmap.api.model.{Identity, IdentityId}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MemoryCustomIdentityDAO extends CustomIdentityDAO {
  private val table: Table[Username, IdentityId, Identity] = HashBasedTable.create

  override def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] =
    save(user, IdentityId.generate, creationRequest)

  override def save(user: Username, identityId: IdentityId, creationRequest: IdentityCreationRequest): Publisher[Identity] =
    SMono.just(identityId)
      .map(creationRequest.asIdentity)
      .doOnNext(identity => table.put(user, identity.id, identity))

  override def list(user: Username): Publisher[Identity] = SFlux.fromIterable(table.row(user).values().asScala)

  override def findByIdentityId(user: Username, identityId: IdentityId): SMono[Identity] =
    SFlux.fromIterable(table.row(user).values().asScala)
      .filter(identity => identity.id.equals(identityId))
      .next()

  override def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): Publisher[Unit] =
    Option(table.get(user, identityId))
      .map(identityUpdate.update)
      .fold(SMono.error[Unit](IdentityNotFoundException(identityId)))(identity => SMono.fromCallable[Unit](() => table.put(user, identityId, identity)))

  override def upsert(user: Username, patch: Identity): SMono[Unit] = SMono.fromCallable[Unit](() => table.put(user, patch.id, patch))

  override def delete(username: Username, ids: Seq[IdentityId]): Publisher[Unit] = SFlux.fromIterable(ids)
    .doOnNext(id => table.remove(username, id))
    .`then`()

  override def delete(username: Username): Publisher[Unit] = SMono.fromCallable(() => table.rowMap().remove(username))
}