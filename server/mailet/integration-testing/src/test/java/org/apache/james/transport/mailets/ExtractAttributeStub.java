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

package org.apache.james.transport.mailets;

import java.util.Optional;
import java.util.function.Consumer;

import jakarta.mail.MessagingException;

import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

public class ExtractAttributeStub extends GenericMailet {

    private static Consumer<Optional<?>> dkimAuthResultInspector;
    private AttributeName name;

    @Override
    public void init() {
        name = AttributeName.of(getInitParameter("attributeName"));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        dkimAuthResultInspector.accept(AttributeUtils.getAttributeValueFromMail(mail, name));
    }

    public static void setDkimAuthResultInspector(Consumer<Optional<?>> inspector) {
        dkimAuthResultInspector = inspector;
    }
}
