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

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, select}
import com.datastax.driver.core.{BoundStatement, PreparedStatement, Row, Session, UDTValue}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.CassandraTypesProvider
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.identity.{CustomIdentityDAO, IdentityCreationRequest, IdentityNotFoundException, IdentityUpdate}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable
import org.apache.james.jmap.cassandra.identity.tables.CassandraCustomIdentityTable.{BCC, EMAIL, HTML_SIGNATURE, ID, MAY_DELETE, NAME, REPLY_TO, TABLE_NAME, TEXT_SIGNATURE, USER}
import org.apache.james.jmap.cassandra.utils.EmailAddressTupleUtil
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.javaapi.CollectionConverters

case class CassandraCustomIdentityDAO @Inject()(session: Session,
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
    .value(MAY_DELETE, bindMarker(MAY_DELETE)))

  val selectAllStatement: PreparedStatement = session.prepare(select()
    .from(TABLE_NAME)
    .where(QueryBuilder.eq(USER, bindMarker(USER))))

  val selectOneStatement: PreparedStatement = session.prepare(select()
    .from(TABLE_NAME)
    .where(QueryBuilder.eq(USER, bindMarker(USER)))
    .and(QueryBuilder.eq(ID, bindMarker(ID))))

  val deleteOneStatement: PreparedStatement = session.prepare(QueryBuilder.delete()
    .from(TABLE_NAME)
    .where(QueryBuilder.eq(USER, bindMarker(USER)))
    .and(QueryBuilder.eq(ID, bindMarker(ID))))

  override def save(user: Username, creationRequest: IdentityCreationRequest): SMono[Identity] = {
    val id = IdentityId.generate
    SMono.just(id)
      .map(creationRequest.asIdentity)
      .flatMap(identity => insert(user, identity))
  }

  override def list(user: Username): SFlux[Identity] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind().setString(USER, user.asString()))
      .map(toIdentity(_)))

  override def update(user: Username, identityId: IdentityId, identityUpdate: IdentityUpdate): SMono[Unit] =
    SMono.fromPublisher(executor.executeSingleRow(selectOneStatement.bind().setString(USER, user.asString())
      .setUUID(ID, identityId.id))
      .switchIfEmpty(Mono.error(() => IdentityNotFoundException(identityId)))
      .map(toIdentity)
      .map(identityUpdate.update)
      .flatMap(patch => insert(user, patch).`then`().asJava()))

  override def delete(username: Username, ids: Seq[IdentityId]): SMono[Unit] =
    SFlux.fromIterable(ids)
      .flatMap(id => executor.executeVoid(deleteOneStatement.bind()
        .setString(USER, username.asString())
        .setUUID(ID, id.id)))
      .`then`()

  private def insert(username: Username, identity: Identity): SMono[Identity] = {
    val insertIdentity: BoundStatement = insertStatement.bind()
      .setString(USER, username.asString())
      .setUUID(ID, identity.id.id)
      .setString(NAME, identity.name.name)
      .setString(EMAIL, identity.email.asString())
      .setString(TEXT_SIGNATURE, identity.textSignature.name)
      .setString(HTML_SIGNATURE, identity.htmlSignature.name)
      .setBool(MAY_DELETE, identity.mayDelete.value)

    identity.replyTo
      .map(listEmailAddress => insertIdentity.setSet(REPLY_TO, toJavaSet(listEmailAddress)))
    identity.bcc
      .map(listEmailAddress => insertIdentity.setSet(BCC, toJavaSet(listEmailAddress)))

    SMono.fromPublisher(executor.executeVoid(insertIdentity)
      .thenReturn(identity))
  }

  private def toIdentity(row: Row): Identity =
    Identity(IdentityId(row.getUUID(ID)),
      IdentityName(row.getString(NAME)),
      new MailAddress(row.getString(EMAIL)),
      toReplyTo(row),
      toBcc(row),
      TextSignature(row.getString(TEXT_SIGNATURE)),
      HtmlSignature(row.getString(HTML_SIGNATURE)),
      MayDeleteIdentity(row.getBool(MAY_DELETE)))

  private def toReplyTo(row: Row): Option[List[EmailAddress]] =
    Option(CollectionConverters.asScala(row.getSet(REPLY_TO, classOf[UDTValue]))
      .toList
      .map(toEmailAddress))

  private def toBcc(row: Row): Option[List[EmailAddress]] =
    Option(CollectionConverters.asScala(row.getSet(BCC, classOf[UDTValue]))
      .toList
      .map(toEmailAddress))

  private def toJavaSet(listEmailAddress: List[EmailAddress]): java.util.Set[UDTValue] =
    CollectionConverters.asJava(listEmailAddress.map(emailAddress =>
      emailAddressTupleUtil.createEmailAddressUDT(emailAddress.name.map(name => name.value), emailAddress.email.asString()))
      .toSet)

  private def toEmailAddress(udtValue: UDTValue): EmailAddress =
    EmailAddress(name = Option(udtValue.getString(CassandraCustomIdentityTable.EmailAddress.NAME)).map(string => EmailerName(string)),
      email = new MailAddress(udtValue.getString(CassandraCustomIdentityTable.EmailAddress.EMAIL)))
}