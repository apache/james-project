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

package org.apache.james.imap.api.display;

import java.util.List;
import java.util.Locale;

/**
 * <p>
 * Immutable bean describing localisation preferences.
 * </p>
 * <p>
 * Two separate sources of information about localisation are available:
 * </p>
 * <ul>
 * <li>A client may ask for a locale (see {@link #getClientPreference()})</li>
 * <li>Localisation preferences may be available from user data(see @link
 * {@link #getUserPreferences()})</li>
 * </ul>
 */
public class Locales {

    private final List<Locale> userPreferences;
    private final Locale clientPreference;

    public Locales(final List<Locale> userPreferences, final Locale clientPreference) {
        super();
        this.userPreferences = userPreferences;
        this.clientPreference = clientPreference;
    }

    /**
     * Gets the locale preferred by the client.
     * 
     * @return when set, the locale currently preferred by the client or null
     *         when no preference has been set by the client
     * @see #getUserPreferences()
     */
    public Locale getClientPreference() {
        return clientPreference;
    }

    /**
     * Gets the list of locales preferred by the user.
     * 
     * @return preferred first not null, possibly empty
     * @see #getUserPreferences()
     */
    public List<Locale> getUserPreferences() {
        return userPreferences;
    }
}
