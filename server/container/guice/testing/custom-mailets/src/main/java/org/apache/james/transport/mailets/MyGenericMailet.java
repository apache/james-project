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

import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

public class MyGenericMailet extends GenericMailet {
    private final MyInterface myInterface;

    @Inject
    public MyGenericMailet(MyInterface myInterface) {
        this.myInterface = myInterface;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        myInterface.doSomething();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MyGenericMailet) {
            MyGenericMailet that = (MyGenericMailet) o;

            return Objects.equals(this.myInterface, that.myInterface);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(myInterface);
    }
}
