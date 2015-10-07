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


package org.apache.mailet.base;

import java.util.Date;

import javax.mail.internet.MailDateFormat;


/**
 * A thread safe wrapper for the <code>javax.mail.internet.MailDateFormat</code> class.
 *
 */
public class RFC822DateFormat extends SynchronizedDateFormat {
    /**
     * A static instance of the RFC822DateFormat, used by toString
     */
    private static RFC822DateFormat instance;

    static {
        instance = new RFC822DateFormat();
    }

    /**
     * This static method allows us to format RFC822 dates without
     * explicitly instantiating an RFC822DateFormat object.
     *
     * @return java.lang.String
     * @param d Date
     *
     * @deprecated This method is not necessary and is preserved for API
     *             backwards compatibility.  Users of this class should
     *             instantiate an instance and use it as they would any
     *             other DateFormat object.
     */
    public static String toString(Date d) {
        return instance.format(d);
    }

    /**
     * Constructor for RFC822DateFormat
     */
    public RFC822DateFormat() {
        super(new MailDateFormat());
    }
}
