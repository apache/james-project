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

package org.apache.james.jmap.core

import com.google.common.collect.ImmutableList
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.james.rrt.api.RecipientValidator
import org.apache.james.rrt.api.RecipientValidator.RecipientRewriteTableCheck
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class JmapRfc8621ConfigurationTest {
  private def configurationWith(properties: (String, String)*): JmapRfc8621Configuration = {
    val underlying = new PropertiesConfiguration()
    // James reads jmap.properties with a comma list delimiter, see PropertiesProvider
    underlying.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    properties.foreach { case (key, value) => underlying.addProperty(key, value) }
    JmapRfc8621Configuration.from(underlying)
  }

  @Test
  def recipientValidationShouldBeDisabledByDefault(): Unit =
    assertThat(configurationWith().validateRecipientsOnSend).isFalse

  @Test
  def recipientValidationShouldBeEnabledWhenConfigured(): Unit =
    assertThat(configurationWith("send.validate.rcpt" -> "true").validateRecipientsOnSend).isTrue

  @Test
  def recipientValidationPolicyShouldDefaultToMappingExists(): Unit =
    assertThat(configurationWith("send.validate.rcpt" -> "true").recipientValidationPolicy)
      .isEqualTo(RecipientValidator.Policy.DEFAULT)

  @Test
  def recipientValidationPolicyShouldBeReadFromItsSubKeys(): Unit =
    assertThat(configurationWith(
      "send.validate.rcpt" -> "true",
      "send.validate.rcpt.enableRecipientRewriteTable" -> "false",
      "send.validate.rcpt.recipientRewriteTableCheck" -> "allMappingsValid").recipientValidationPolicy)
      .isEqualTo(new RecipientValidator.Policy(false, RecipientRewriteTableCheck.ALL_TARGETS_HAVE_LOCAL_MAILBOX))

  @Test
  def shouldThrowOnUnsupportedRecipientRewriteTableCheck(): Unit =
    assertThatThrownBy(() => configurationWith("send.validate.rcpt.recipientRewriteTableCheck" -> "invalid"))
      .hasMessageContaining("unsupported value 'invalid'")

  @Test
  def extraValidationsShouldBeEmptyByDefault(): Unit =
    assertThat(configurationWith().extraEmailSubmissionValidations).isEmpty

  @Test
  def extraValidationsShouldBeReadAsAList(): Unit =
    assertThat(configurationWith("send.extra.validations" -> "com.a.First,com.b.Second").extraEmailSubmissionValidations)
      .isEqualTo(ImmutableList.of("com.a.First", "com.b.Second"))
}
