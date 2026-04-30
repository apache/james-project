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
import com.google.inject.name.Named
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventBus}
import org.apache.james.jmap.api.identity.{AllCustomIdentitiesDeleted, CustomIdentityCreated, CustomIdentityDAO, CustomIdentityDeleted, CustomIdentityUpdated, IdentityCreationRequest, IdentityNotFoundException, IdentityUpdate}
import org.apache.james.jmap.api.model.{AccountId, Identity, IdentityId}
import org.apache.james.jmap.change.AccountIdRegistrationKey
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MemoryCustomIdentityDAO @Inject()(@Named("JMAP") eventBus: EventBus) extends CustomIdentityDAO {
  private val table: Table[Username, IdentityId, Identity] = HashBasedTable.create

  override def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] =
    save(user, IdentityId.generate, creationRequest)

  override def save(user: Username, identityId: IdentityId, creationRequest: IdentityCreationRequest): Publisher[Identity] =
    SMono.just(identityId)
      .map(creationRequest.asIdentity)
      .doOnNext(identity => table.put(user, identity.id, identity))
      .flatMap(identity =>
        SMono.fromPublisher(eventBus.dispatch(CustomIdentityCreated(Event.EventId.random(), user, identity), AccountIdRegistrationKey(AccountId.fromUsername(user))))
          .`then`(SMono.just(identity)))

  override def list(user: Username): Publisher[Identity] = SFlux.fromIterable(table.row(user).values().asScala)

  override def findByIdentityId(user: Username, identityId: IdentityId): SMono[Identity] =
    SFlux.fromIterable(table.row(user).values().asScala)
      .filter(identity => identity.id.equals(identityId))
      .next()

  override def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): Publisher[Unit] =
    Option(table.get(user, identityId))
      .map(identityUpdate.update)
      .fold(SMono.error[Unit](IdentityNotFoundException(identityId)))(updatedIdentity =>
        SMono.fromCallable[Unit](() => table.put(user, identityId, updatedIdentity))
          .flatMap(_ => SMono.fromPublisher(eventBus.dispatch(CustomIdentityUpdated(Event.EventId.random(), user, updatedIdentity), AccountIdRegistrationKey(AccountId.fromUsername(user))))
            .`then`()))

  override def upsert(user: Username, patch: Identity): SMono[Unit] =
    SMono.fromCallable[Unit](() => table.put(user, patch.id, patch))
      .flatMap(_ => SMono.fromPublisher(eventBus.dispatch(CustomIdentityUpdated(Event.EventId.random(), user, patch), AccountIdRegistrationKey(AccountId.fromUsername(user))))
        .`then`())

  override def delete(username: Username, ids: Set[IdentityId]): Publisher[Unit] =
    SMono.fromCallable[Unit](() => ids.foreach(id => table.remove(username, id)))
      .flatMap(_ => SMono.fromPublisher(eventBus.dispatch(CustomIdentityDeleted(Event.EventId.random(), username, ids), AccountIdRegistrationKey(AccountId.fromUsername(username))))
        .`then`())

  override def delete(username: Username): Publisher[Unit] =
    SMono.fromCallable(() => {
      val ids = table.row(username).keySet().asScala.toSet
      table.rowMap().remove(username)
      ids
    })
      .flatMap(ids => SMono.fromPublisher(eventBus.dispatch(AllCustomIdentitiesDeleted(Event.EventId.random(), username, ids), AccountIdRegistrationKey(AccountId.fromUsername(username))))
        .`then`())
}
