/****************************************************************
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

package org.apache.james.jmap.mail

import java.nio.charset.{CoderResult, StandardCharsets}
import java.nio.{ByteBuffer, CharBuffer}

import org.apache.james.jmap.mail.EmailGetRequest.{MaxBodyValueBytes, ZERO}

case class IsEncodingProblem(value: Boolean) extends AnyVal
case class IsTruncated(value: Boolean) extends AnyVal

case class EmailBodyValue(value: String,
                          isEncodingProblem: IsEncodingProblem,
                          isTruncated: IsTruncated) {
  def truncate(maxBodyValueBytes: Option[MaxBodyValueBytes]): EmailBodyValue = maxBodyValueBytes match {
    case None => this
    case Some(ZERO) => this
    case Some(truncateAt) if truncateAt.value > value.getBytes(StandardCharsets.UTF_8).length => this
    case Some(truncateAt) =>
      val array: Array[Byte] = new Array[Byte](truncateAt.value)
      val outBuf: ByteBuffer = ByteBuffer.wrap(array)
      val result: CoderResult = StandardCharsets.UTF_8
        .newEncoder()
        .encode(CharBuffer.wrap(value.toCharArray), outBuf, true)
      val truncatedValue: String = new String(array, StandardCharsets.UTF_8)

      EmailBodyValue(
        value = truncatedValue,
        isEncodingProblem = IsEncodingProblem(result.isError),
        isTruncated = IsTruncated(true))
  }
}