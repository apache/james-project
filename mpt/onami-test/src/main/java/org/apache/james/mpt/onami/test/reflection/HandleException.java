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

package org.apache.james.mpt.onami.test.reflection;

import static java.lang.String.format;

/**
 * Exception thrown by a {@link ClassVisitor} when a error occurs.
 */
public final class HandleException
    extends Exception
{

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new HandleException with the specified detail message and cause.
     *
     * @param message  detail message
     * @param cause the cause
     */
    public HandleException( String message, Throwable cause )
    {
        super( message, cause );
    }

    /**
     * Constructs a new HandleException with the specified detail message.
     *
     * @param message a format string
     * @param args arguments referenced by the format specifiers in the format string
     * @see String#format(String, Object...)
     */
    public HandleException( String message, Object...args )
    {
        super( format( message, args ) );
    }

    /**
     * Constructs a new HandleException with the specified cause.
     *
     * @param cause the cause
     */
    public HandleException( Throwable cause )
    {
        super( cause );
    }

}
