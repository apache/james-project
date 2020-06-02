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

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval.Closed

object UnsignedInt {
  //Unsigned int between [0, 2^53]
  type UnsignedIntConstraint = Closed[0L, 9007199254740992L]
  type UnsignedInt = Long Refined UnsignedIntConstraint

  def validate(value: Long): Either[NumberFormatException, UnsignedInt] =
    refined.refineV[UnsignedIntConstraint](value) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new NumberFormatException(error))
    }

  def liftOrThrow(value: Long): UnsignedInt =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }

}
