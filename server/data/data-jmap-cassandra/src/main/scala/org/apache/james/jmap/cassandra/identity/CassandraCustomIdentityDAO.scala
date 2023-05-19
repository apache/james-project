/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.cassandra.identity

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement, Row}
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.CassandraTypesProvider
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.identity.{CustomIdentityDAO, IdentityCreationRequest, IdentityNotFoundException, IdentityUpdate}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable.{BCC, EMAIL, HTML_SIGNATURE, ID, MAY_DELETE, NAME, REPLY_TO, SORT_ORDER, TABLE_NAME, TEXT_SIGNATURE, USER}
import org.apache.james.jmap.cassandra.utils.EmailAddressTupleUtil
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.javaapi.CollectionConverters

case class CassandraCustomIdentityDAO @Inject()(session: CqlSession,
                                                typesProvider: CassandraTypesProvider) extends CustomIdentityDAO {
  val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  val emailAddressTupleUtil: EmailAddressTupleUtil = EmailAddressTupleUtil(typesProvider)

  val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USER, bindMarker(USER))
    .value(ID, bindMarker(ID))
    .value(NAME, bindMarker(NAME))
    .value(EMAIL, bindMarker(EMAIL))
    .value(REPLY_TO, bindMarker(REPLY_TO))
    .value(BCC, bindMarker(BCC))
    .value(TEXT_SIGNATURE, bindMarker(TEXT_SIGNATURE))
    .value(HTML_SIGNATURE, bindMarker(HTML_SIGNATURE))
    .value(MAY_DELETE, bindMarker(MAY_DELETE))
    .value(SORT_ORDER, bindMarker(SORT_ORDER))
    .build())

  val selectAllStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  val selectOneStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(ID).isEqualTo(bindMarker(ID))
    .build())

  val deleteOneStatement: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(ID).isEqualTo(bindMarker(ID))
    .build())

  val deleteAllStatement: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  override def save(user: Username, creationRequest: IdentityCreationRequest): SMono[Identity] =
    save(user, IdentityId.generate, creationRequest)

  override def save(user: Username, identityId: IdentityId, creationRequest: IdentityCreationRequest): SMono[Identity] =
    SMono.just(identityId)
      .map(creationRequest.asIdentity)
      .flatMap(identity => insert(user, identity))

  override def list(user: Username): SFlux[Identity] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind().setString(USER, user.asString()))
      .map(toIdentity))

  override def findByIdentityId(user: Username, identityId: IdentityId): SMono[Identity] =
    SMono.fromPublisher(executor.executeSingleRow(selectOneStatement.bind().setString(USER, user.asString())
      .setUuid(ID, identityId.id))
      .map(toIdentity))

  override def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): SMono[Unit] =
    SMono.fromPublisher(executor.executeSingleRow(selectOneStatement.bind().setString(USER, user.asString())
      .setUuid(ID, identityId.id))
      .switchIfEmpty(Mono.error(() => IdentityNotFoundException(identityId)))
      .map(toIdentity)
      .map(identityUpdate.update(_))
      .flatMap(patch => insert(user, patch).`then`().asJava()))

  override def upsert(user: Username, patch: Identity): SMono[Unit] =
    insert(user, patch).`then`()

  override def delete(username: Username, ids: Seq[IdentityId]): SMono[Unit] =
    SFlux.fromIterable(ids)
      .flatMap(id => executor.executeVoid(deleteOneStatement.bind()
        .setString(USER, username.asString())
        .setUuid(ID, id.id)))
      .`then`()

  override def delete(username: Username): SMono[Unit] =
    SMono(executor.executeVoid(deleteOneStatement.bind()
        .setString(USER, username.asString())))
      .`then`()

  private def insert(username: Username, identity: Identity): SMono[Identity] = {
    val replyTo: java.util.Set[UdtValue] = toJavaSet(identity.replyTo.getOrElse(List()))
    val bcc: java.util.Set[UdtValue] = toJavaSet(identity.bcc.getOrElse(List()))
    val insertIdentity: BoundStatement = insertStatement.bind()
      .setString(USER, username.asString())
      .setUuid(ID, identity.id.id)
      .setString(NAME, identity.name.name)
      .setString(EMAIL, identity.email.asString())
      .setString(TEXT_SIGNATURE, identity.textSignature.name)
      .setString(HTML_SIGNATURE, identity.htmlSignature.name)
      .setBoolean(MAY_DELETE, identity.mayDelete.value)
      .setInt(SORT_ORDER, identity.sortOrder)
      .setSet(REPLY_TO, replyTo, classOf[UdtValue])
      .setSet(BCC, bcc, classOf[UdtValue])

    SMono.fromPublisher(executor.executeVoid(insertIdentity)
      .thenReturn(identity))
  }

  private def toIdentity(row: Row): Identity =
    Identity(id = IdentityId(row.getUuid(ID)),
      name = IdentityName(row.getString(NAME)),
      email = new MailAddress(row.getString(EMAIL)),
      replyTo = toReplyTo(row),
      bcc = toBcc(row),
      textSignature = TextSignature(row.getString(TEXT_SIGNATURE)),
      htmlSignature = HtmlSignature(row.getString(HTML_SIGNATURE)),
      mayDelete = MayDeleteIdentity(row.getBoolean(MAY_DELETE)),
      sortOrder = Option(row.getInt(SORT_ORDER)).getOrElse(Identity.DEFAULT_SORTORDER))

  private def toReplyTo(row: Row): Option[List[EmailAddress]] =
    Option(CollectionConverters.asScala(row.getSet(REPLY_TO, classOf[UdtValue]))
      .toList
      .map(toEmailAddress))

  private def toBcc(row: Row): Option[List[EmailAddress]] =
    Option(CollectionConverters.asScala(row.getSet(BCC, classOf[UdtValue]))
      .toList
      .map(toEmailAddress))

  private def toJavaSet(listEmailAddress: List[EmailAddress]): java.util.Set[UdtValue] =
    CollectionConverters.asJava(listEmailAddress.map(emailAddress =>
      emailAddressTupleUtil.createEmailAddressUDT(emailAddress.name.map(name => name.value), emailAddress.email.asString()))
      .toSet)

  private def toEmailAddress(udtValue: UdtValue): EmailAddress =
    EmailAddress(name = Option(udtValue.getString(CassandraCustomIdentityTable.EmailAddress.NAME)).map(string => EmailerName(string)),
      email = new MailAddress(udtValue.getString(CassandraCustomIdentityTable.EmailAddress.EMAIL)))
}