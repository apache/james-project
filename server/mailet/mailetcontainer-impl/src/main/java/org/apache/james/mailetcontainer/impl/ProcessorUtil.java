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

package org.apache.james.mailetcontainer.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.slf4j.Logger;

public class ProcessorUtil {

    /**
     * This is a helper method that updates the state of the mail object to
     * Mail.ERROR as well as recording the exception to the log
     * 
     * @param me
     *            the exception to be handled
     * @param mail
     *            the mail being processed when the exception was generated
     * @param offendersName
     *            the matcher or mailet than generated the exception
     * @param nextState
     *            the next state to set
     */
    public static void handleException(Throwable me, Mail mail, String offendersName, String nextState, Logger logger) {
        mail.setState(nextState);
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        String exceptionBuffer = "Exception calling " + offendersName + ": " + me.getMessage();
        out.println(exceptionBuffer);
        Throwable e = me;
        while (e != null) {
            e.printStackTrace(out);
            if (e instanceof MessagingException) {
                e = ((MessagingException) e).getNextException();
            } else {
                e = null;
            }
        }
        String errorString = sout.toString();
        mail.setErrorMessage(errorString);
        logger.error(errorString);
        mail.setAttribute(new Attribute(Mail.MAILET_ERROR, AttributeValue.ofUnserializable(me)));
    }

    /**
     * Checks that all objects in this class are of the form MailAddress.
     * 
     * @throws MessagingException
     *             when the <code>Collection</code> contains objects that are
     *             not <code>MailAddress</code> objects
     */
    public static void verifyMailAddresses(Collection<MailAddress> col) throws MessagingException {
        try {
            MailAddress[] addresses = col.toArray(MailAddress[]::new);

            // Why is this here? According to the javadoc for
            // java.util.Collection.toArray(Object[]), this should
            // never happen. The exception will be thrown.
            if (addresses.length != col.size()) {
                throw new MailetException("The recipient list contains objects other than MailAddress objects");
            }
        } catch (ArrayStoreException ase) {
            throw new MailetException("The recipient list contains objects other than MailAddress objects");
        }
    }
}
