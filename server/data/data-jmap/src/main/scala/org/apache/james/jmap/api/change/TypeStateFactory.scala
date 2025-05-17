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

package org.apache.james.jmap.api.change

import jakarta.inject.Inject
import org.apache.james.jmap.api.model.TypeName
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._

case class TypeStateFactory @Inject()(setTypeName: java.util.Set[TypeName]) {
  val logger: Logger = LoggerFactory.getLogger(classOf[TypeStateFactory])
  val all: Set[TypeName] = setTypeName.asScala.toSet

  def strictParse(string: String): Either[IllegalArgumentException, TypeName] =
    all.flatMap(_.parse(string))
      .headOption
      .map(Right(_))
      .getOrElse(Left(new IllegalArgumentException(s"Unknown typeName $string")))

  def lenientParse(string: String): Option[TypeName] =
    all.flatMap(_.parse(string)).headOption match {
      case Some(value) => Some(value)
      case None =>
        logger.warn("Leniently ignore {} while parsing TypeName", string)
        None
    }
}
