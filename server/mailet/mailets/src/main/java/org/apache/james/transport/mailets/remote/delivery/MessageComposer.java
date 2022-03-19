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

package org.apache.james.transport.mailets.remote.delivery;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

import org.apache.mailet.Mail;

public class MessageComposer {

    private final RemoteDeliveryConfiguration configuration;

    public MessageComposer(RemoteDeliveryConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Try to return a usefull logString created of the Exception which was
     * given. Return null if nothing usefull could be done
     *
     * @param e The MessagingException to use
     * @return logString
     */
    private String fromException(Exception e) {
        if (e.getClass().getName().endsWith(".SMTPSendFailedException")) {
            return "RemoteHost said: " + e.getMessage();
        } else if (e instanceof SendFailedException) {
            SendFailedException exception = (SendFailedException) e;

            // No error
            if (exception.getInvalidAddresses().length == 0 && exception.getValidUnsentAddresses().length == 0) {
                return null;
            }

            Exception ex;
            StringBuilder sb = new StringBuilder();
            boolean smtpExFound = false;
            sb.append("RemoteHost said:");

            if (e instanceof MessagingException) {
                while ((ex = ((MessagingException) e).getNextException()) != null && ex instanceof MessagingException) {
                    e = ex;
                    if (ex.getClass().getName().endsWith(".SMTPAddressFailedException")) {
                        try {
                            InternetAddress ia = (InternetAddress) invokeGetter(ex, "getAddress");
                            sb.append(" ( ").append(ia).append(" - [").append(ex.getMessage().replace("\\n", "")).append("] )");
                            smtpExFound = true;
                        } catch (IllegalStateException ise) {
                            // Error invoking the getAddress method
                        } catch (ClassCastException cce) {
                            // The getAddress method returned something
                            // different than InternetAddress
                        }
                    }
                }
            }
            if (!smtpExFound) {
                boolean invalidAddr = false;
                sb.append(" ( ");

                if (exception.getInvalidAddresses().length > 0) {
                    sb.append(Arrays.toString(exception.getInvalidAddresses()));
                    invalidAddr = true;
                }
                if (exception.getValidUnsentAddresses().length > 0) {
                    if (invalidAddr) {
                        sb.append(" ");
                    }
                    sb.append(Arrays.toString(exception.getValidUnsentAddresses()));
                }
                sb.append(" - [");
                sb.append(exception.getMessage().replace("\\n", ""));
                sb.append("] )");
            }
            return sb.toString();
        }
        return null;
    }

    public String composeFailLogMessage(Mail mail, ExecutionResult executionResult) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        out.print(permanentAsString(executionResult.isPermanent()) + " exception delivering mail (" + mail.getName()
            + ")" + retrieveExceptionLog(executionResult.getException().orElse(null)) + ": ");
        if (configuration.isDebug()) {
            executionResult.getException().ifPresent(e -> e.printStackTrace(out));
        }
        return sout.toString();
    }

    private String permanentAsString(boolean permanent) {
        if (permanent) {
            return "Permanent";
        }
        return "Temporary";
    }

    private String retrieveExceptionLog(Exception ex) {
        String exceptionLog = fromException(ex);
        if (exceptionLog != null) {
            return ". " + exceptionLog;
        }
        return "";
    }

    private Object invokeGetter(Object target, String getter) {
        try {
            Method getAddress = target.getClass().getMethod(getter);
            return getAddress.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return new IllegalStateException("Exception invoking " + getter + " on a " + target.getClass() + " object");
        }
    }

}
