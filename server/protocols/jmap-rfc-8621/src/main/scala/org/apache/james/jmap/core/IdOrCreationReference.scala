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

import org.apache.james.jmap.core.Id.Id
import play.api.libs.json.{Format, JsError, JsString, JsSuccess}

/** An ID token at a boundary where a creation reference may still be unresolved.
  * Only the reference suffix is subject to the 255-character ID limit.
  */
sealed trait IdOrCreationReference {
  def value: String

  def creationId: Option[ClientId]

  final override def toString: String = value
}

object IdOrCreationReference {
  private case class ExistingId(id: Id) extends IdOrCreationReference {
    override def value: String = id.value

    override def creationId: Option[ClientId] = None
  }

  private case class CreationReference(id: Id) extends IdOrCreationReference {
    override def value: String = "#" + id.value

    override def creationId: Option[ClientId] = Some(ClientId(id))
  }

  def validate(value: String): Either[IllegalArgumentException, IdOrCreationReference] =
    if (value.startsWith("#")) {
      Id.validate(value.substring(1)).map(CreationReference)
    } else {
      Id.validate(value).map(ExistingId)
    }

  /** Adapt a legacy Id without strengthening its validation (which permits a bare '#'). */
  def fromId(id: Id): IdOrCreationReference =
    validate(id.value).getOrElse(ExistingId(id))

  implicit val format: Format[IdOrCreationReference] = Format(
    {
      case JsString(value) => validate(value).fold(e => JsError(e.getMessage), JsSuccess(_))
      case _ => JsError("Id or creation reference needs to be represented by a JsString")
    },
    value => JsString(value.value))
}
