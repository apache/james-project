/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.user.ldap.retry.naming.directory;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.james.user.ldap.retry.ExceptionRetryHandler;
import org.apache.james.user.ldap.retry.api.RetrySchedule;
import org.apache.james.user.ldap.retry.naming.LoggingRetryHandler;
import org.apache.james.user.ldap.retry.naming.RetryingContext;

/**
 * <code>RetryingDirContext</code> retries the methods defined by <code>javax.naming.directory.DirContext</code>
 * according to the specified schedule. 
 * 
 * @see ExceptionRetryHandler
 * @see org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy
 * @see javax.naming.directory.DirContext
 */
public abstract class RetryingDirContext extends RetryingContext implements DirContext {


    /**
     * Creates a new instance of RetryingDirContext.
     *
     * @param schedule
     * @param maxRetries
     * @throws NamingException
     */
    public RetryingDirContext(RetrySchedule schedule, int maxRetries)
            throws NamingException {
        super(schedule, maxRetries);
    }

    @Override
    public void bind(final Name name, final Object obj, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).bind(name, obj, attrs);
                return null;
            }
        }.perform();
    }

    @Override
    public void bind(final String name, final Object obj, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).bind(name, obj, attrs);
                return null;
            }
        }.perform();
    }

    @Override
    public DirContext createSubcontext(final Name name, final Attributes attrs)
            throws NamingException {
        return (DirContext) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).createSubcontext(name, attrs);
            }
        }.perform();
    }

    @Override
    public DirContext createSubcontext(final String name, final Attributes attrs)
            throws NamingException {
        return (DirContext) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).createSubcontext(name, attrs);
            }
        }.perform();
    }

    @Override
    public Attributes getAttributes(final Name name) throws NamingException {
        return (Attributes) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).getAttributes(name);
            }
        }.perform();
    }

    @Override
    public Attributes getAttributes(final String name) throws NamingException {
        return (Attributes) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).getAttributes(name);
            }
        }.perform();
    }

    @Override
    public Attributes getAttributes(final Name name, final String[] attrIds) throws NamingException {
        return (Attributes) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).getAttributes(name, attrIds);
            }
        }.perform();
    }

    @Override
    public Attributes getAttributes(final String name, final String[] attrIds)
            throws NamingException {
        return (Attributes) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).getAttributes(name, attrIds);
            }
        }.perform();
    }

    @Override
    public DirContext getSchema(final Name name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingDirContext(getSchedule(), getMaxRetries()) {

            @Override
            public DirContext newDelegate() throws NamingException {
                return ((DirContext) context).getSchema(name);
            }
        };
    }

    @Override
    public DirContext getSchema(final String name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingDirContext(getSchedule(), getMaxRetries()) {

            @Override
            public DirContext newDelegate() throws NamingException {
                return ((DirContext) context).getSchema(name);
            }
        };
    }

    @Override
    public DirContext getSchemaClassDefinition(final Name name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingDirContext(getSchedule(), getMaxRetries()) {

            @Override
            public DirContext newDelegate() throws NamingException {
                return ((DirContext) context).getSchemaClassDefinition(name);
            }
        };
    }

    @Override
    public DirContext getSchemaClassDefinition(final String name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingDirContext(getSchedule(), getMaxRetries()) {

            @Override
            public DirContext newDelegate() throws NamingException {
                return ((DirContext) context).getSchemaClassDefinition(name);
            }
        };
    }

    @Override
    public void modifyAttributes(final Name name, final ModificationItem[] mods)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).modifyAttributes(name, mods);
                return null;
            }
        }.perform();
    }

    @Override
    public void modifyAttributes(final String name, final ModificationItem[] mods)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).modifyAttributes(name, mods);
                return null;
            }
        }.perform();
    }

    @Override
    public void modifyAttributes(final Name name, final int modOp, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).modifyAttributes(name, modOp, attrs);
                return null;
            }
        }.perform();
    }

    @Override
    public void modifyAttributes(final String name, final int modOp, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).modifyAttributes(name, modOp, attrs);
                return null;
            }
        }.perform();
    }

    @Override
    public void rebind(final Name name, final Object obj, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).rebind(name, obj, attrs);
                return null;
            }
        }.perform();
    }

    @Override
    public void rebind(final String name, final Object obj, final Attributes attrs)
            throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                ((DirContext) getDelegate()).rebind(name, obj, attrs);
                return null;
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final Name name,
            final Attributes matchingAttributes)
            throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, matchingAttributes);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final String name,
            final Attributes matchingAttributes)
            throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, matchingAttributes);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final Name name,
            final Attributes matchingAttributes,
            String[] attributesToReturn) throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, matchingAttributes);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final String name,
            final Attributes matchingAttributes,
            final String[] attributesToReturn) throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, matchingAttributes,
                        attributesToReturn);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final Name name, final String filter,
            final SearchControls cons)
            throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, filter, cons);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final String name, final String filter,
            final SearchControls cons)
            throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, filter, cons);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final Name name, final String filterExpr,
            final Object[] filterArgs, final SearchControls cons) throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, filterExpr, filterArgs, cons);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<SearchResult> search(final String name, final String filterExpr,
            final Object[] filterArgs, final SearchControls cons) throws NamingException {
        return (NamingEnumeration<SearchResult>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {

            @Override
            public Object operation() throws NamingException {
                return ((DirContext) getDelegate()).search(name, filterExpr, filterArgs, cons);
            }
        }.perform();
    }

}
