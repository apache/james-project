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

package org.apache.james.transport.mailets.jsieve;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.mail.MessagingException;

import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionDiscard;
import org.apache.jsieve.mail.ActionFileInto;
import org.apache.jsieve.mail.ActionKeep;
import org.apache.jsieve.mail.ActionRedirect;
import org.apache.jsieve.mail.ActionReject;
import org.apache.jsieve.mail.optional.ActionVacation;
import org.apache.mailet.Mail;

/**
 * Dynamically dispatches an Action depending on the type of Action received at runtime.
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class ActionDispatcher {
    /**
     * A Map keyed by the type of Action. The values are the methods to invoke to
     * handle the Action.
     * <Action, MailAction>
     */
    private ConcurrentMap<Class<?>, MailAction> fieldMailActionMap;

    /**
     * Constructor for ActionDispatcher.
     *
     * @throws NoSuchMethodException
     */
    public ActionDispatcher() {
        super();
        setMethodMap(defaultMethodMap());
    }

    /**
     * Method execute executes the passed Action by invoking the method mapped by the
     * receiver with a parameter of the EXACT type of Action.
     *
     * @param anAction not null
     * @param aMail    not null
     * @param context  not null
     * @throws MessagingException
     */
    public void execute(final Action anAction, final Mail aMail, final ActionContext context) throws MessagingException {
        final MailAction mailAction = getMethodMap().get(anAction.getClass());
        mailAction.execute(anAction, aMail, context);
    }

    /**
     * Returns the methodMap.
     *
     * @return Map
     */
    public ConcurrentMap<Class<?>, MailAction> getMethodMap() {
        return fieldMailActionMap;
    }

    /**
     * Returns a new methodMap.
     *
     * @return Map
     */
    private ConcurrentMap<Class<?>, MailAction> defaultMethodMap() {
        final ConcurrentMap<Class<?>, MailAction> actionMap = new ConcurrentHashMap<>(4);
        actionMap.put(ActionFileInto.class, new FileIntoAction());
        actionMap.put(ActionKeep.class, new KeepAction());
        actionMap.put(ActionRedirect.class, new RedirectAction());
        actionMap.put(ActionReject.class, new RejectAction());
        actionMap.put(ActionVacation.class, new VacationAction());
        actionMap.put(ActionDiscard.class, new DiscardAction());
        return actionMap;
    }

    /**
     * Sets the mail action mail.
     *
     * @param mailActionMap <Action, MailAction> not null
     */
    protected void setMethodMap(ConcurrentMap<Class<?>, MailAction> mailActionMap) {
        fieldMailActionMap = mailActionMap;
    }
}
