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
 * ***************************************************************/

package org.apache.james.jmap.model

import com.google.common.math.LongMath
import org.slf4j.{Logger, LoggerFactory}

object UnsignedInt {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[UnsignedInt])
  private val VALIDATION_MESSAGE = "value should be positive and less than 2^53"

  trait Factory[T] {
    def from(value: Long): T
  }

  private val ZERO_VALUE: Long = 0
  val MAX_VALUE: Long = LongMath.pow(2, 53)
  val ZERO = UnsignedInt.apply(ZERO_VALUE)

  val DEFAULT_FACTORY: UnsignedInt.Factory[Option[UnsignedInt]] = (value: Long) => Some(value)
    .filter(UnsignedInt.isValid)
    .map(UnsignedInt.apply)

  val BOUND_SANITIZING_FACTORY: UnsignedInt.Factory[UnsignedInt] = {
    case x if x < ZERO_VALUE =>
      LOGGER.warn("Received a negative Number")
      UnsignedInt.apply(ZERO_VALUE)
    case x if x > MAX_VALUE =>
      LOGGER.warn("Received a too big Number")
      UnsignedInt.apply(MAX_VALUE)
    case value => UnsignedInt.apply(value)
  }

  def fromLong(value: Long) = apply(value)

  private def isValid(value: Long) = value >= ZERO_VALUE && value <= MAX_VALUE

  implicit def asValidNumber(x: Long): UnsignedInt = apply(x)

  def apply(value: Long): UnsignedInt = {
    require(UnsignedInt.isValid(value), UnsignedInt.VALIDATION_MESSAGE)

    new UnsignedInt(value)
  }
}

final case class UnsignedInt private(value: Long) extends Ordered[UnsignedInt] {
  def asLong: Long = value

  override def compare(that: UnsignedInt): Int = this.value.compare(that.value)
}
