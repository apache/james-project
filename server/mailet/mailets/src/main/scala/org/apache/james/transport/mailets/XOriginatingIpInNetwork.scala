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

package org.apache.james.transport.mailets

import java.util.{Collection => JavaCollection}

import com.google.common.collect.ImmutableList
import org.apache.james.core.MailAddress
import org.apache.james.transport.matchers.AbstractNetworkMatcher
import org.apache.mailet.Mail

object XOriginatingIpInNetwork {
  val X_ORIGINATING_IP: String = "X-Originating-IP"
}

/**
  * <p>
  * Checks the first X_ORIGINATING_IP IP address against a comma-delimited list
  * of IP addresses, domain names or sub-nets.
  * </p>
  * <p>
  * See [[AbstractNetworkMatcher]] for details on how to specify entries.
  * </p>
  */
class XOriginatingIpInNetwork extends AbstractNetworkMatcher {
  override def `match`(mail: Mail): JavaCollection[MailAddress] = matchOnOriginatingAddr(mail).getOrElse(ImmutableList.of())

  def matchOnOriginatingAddr(mail: Mail): Option[JavaCollection[MailAddress]] =
    Option(mail.getMessage.getHeader(XOriginatingIpInNetwork.X_ORIGINATING_IP))
    .flatMap(_.headOption)
    .map(normalizeIP)
    .filter(matchNetwork)
    .map(_ => mail.getRecipients)

  private def normalizeIP(ip: String): String = ip.replace("[", "").replace("]", "")
}
