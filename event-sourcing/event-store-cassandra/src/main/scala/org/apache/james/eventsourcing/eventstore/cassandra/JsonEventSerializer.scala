 /***************************************************************
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
package org.apache.james.eventsourcing.eventstore.cassandra

import java.io.IOException
import java.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.collect.ImmutableSet
import jakarta.inject.{Inject, Named}
import org.apache.james.eventsourcing.Event
import org.apache.james.eventsourcing.eventstore.cassandra.dto.{EventDTO, EventDTOModule}
import org.apache.james.json.{DTO, DTOModule, JsonGenericSerializer}

import scala.annotation.varargs
import scala.jdk.CollectionConverters._

object JsonEventSerializer {

  def forModules(modules: util.Set[_ <: EventDTOModule[_ <: Event, _ <: EventDTO]]): RequireNestedConfiguration =
    (nestedTypesModules: util.Set[DTOModule[_, _ <: DTO]]) =>
      new JsonEventSerializer(ImmutableSet.copyOf(modules), ImmutableSet.copyOf(nestedTypesModules))

  @SafeVarargs
  @varargs
  def forModules(modules: EventDTOModule[_ <: Event, _ <: EventDTO]*): RequireNestedConfiguration = forModules(ImmutableSet.copyOf(modules.toArray))

  trait RequireNestedConfiguration {
    def withNestedTypeModules(modules: util.Set[DTOModule[_, _ <: DTO]]): JsonEventSerializer

    def withNestedTypeModules(modules: Set[DTOModule[_, _ <: DTO]]): JsonEventSerializer = withNestedTypeModules(modules.asJava)

    @varargs
    def withListOfNestedTypeModules(modules: DTOModule[_, _ <: DTO]*): JsonEventSerializer = withNestedTypeModules(ImmutableSet.copyOf(modules.toArray))

    @varargs
    def withNestedTypeModules(modules: util.Set[DTOModule[_, _ <: DTO]]*): JsonEventSerializer = withNestedTypeModules(modules.toList.flatMap(_.asScala).toSet)

    def withoutNestedType: JsonEventSerializer = withNestedTypeModules(Set[DTOModule[_, _ <: DTO]]())
  }

  class InvalidEventException(original: JsonGenericSerializer.InvalidTypeException) extends RuntimeException(original)

  class UnknownEventException(original: JsonGenericSerializer.UnknownTypeException) extends RuntimeException(original)

}

class JsonEventSerializer @Inject()private(modules: util.Set[EventDTOModule[_ <: Event, _ <: EventDTO]],
                                            @Named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME)
                                            nestedTypesModules: util.Set[DTOModule[_, _ <: DTO]]) {

  private val jsonGenericSerializer: JsonGenericSerializer[Event, EventDTO] = JsonGenericSerializer
    .forModules(modules)
    .withNestedTypeModules(nestedTypesModules)

  @throws[JsonProcessingException]
  def serialize(event: Event): String = try jsonGenericSerializer.serialize(event)
  catch {
    case e: JsonGenericSerializer.UnknownTypeException =>
      throw new JsonEventSerializer.UnknownEventException(e)
  }

  @throws[IOException]
  def deserialize(value: String): Event = try jsonGenericSerializer.deserialize(value)
  catch {
    case e: JsonGenericSerializer.UnknownTypeException =>
      throw new JsonEventSerializer.UnknownEventException(e)
    case e: JsonGenericSerializer.InvalidTypeException =>
      throw new JsonEventSerializer.InvalidEventException(e)
  }
}