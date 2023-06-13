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

import javax.mail.MessagingException;

import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionDiscard;
import org.apache.jsieve.mail.ActionFileInto;
import org.apache.jsieve.mail.ActionKeep;
import org.apache.jsieve.mail.ActionRedirect;
import org.apache.jsieve.mail.ActionReject;
import org.apache.jsieve.mail.optional.ActionVacation;
import org.apache.mailet.Mail;

import com.google.common.collect.ImmutableMap;

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
    private static final ImmutableMap<Class<?>, MailAction> MAIL_ACTIONS = ImmutableMap.of(
            ActionFileInto.class, new FileIntoAction(),
            ActionKeep.class, new KeepAction(),
            ActionRedirect.class, new RedirectAction(),
            ActionReject.class, new RejectAction(),
            ActionVacation.class, new VacationAction(),
            ActionDiscard.class, new DiscardAction());

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
        MAIL_ACTIONS.get(anAction.getClass())
                .execute(anAction, aMail, context);
    }
}
