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
package org.apache.james.container.spring.bean.factory.mailetcontainer;

import org.apache.james.container.spring.bean.factory.AbstractBeanFactory;
import org.apache.mailet.MailetException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

public abstract class AbstractLoaderBeanFactory<T> extends AbstractBeanFactory {

    /**
     * Load the class for the given name. If the name is not a full classname
     * (including package) it will get suffixed with
     * {@link #getStandardPackage()}
     * 
     * @param name
     * @return instance
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    protected T load(String name) throws ClassNotFoundException {
        String fullName;
        if (name.indexOf(".") < 1) {
            fullName = getStandardPackage() + "." + name;
        } else {
            fullName = name;
        }
        // Use the classloader which is used for bean instance stuff
        Class<T> c = (Class<T>) getBeanFactory().getBeanClassLoader().loadClass(fullName);
        @SuppressWarnings("deprecation")
        T t = (T) getBeanFactory().createBean(c, AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT, true);
        return t;

    }

    /**
     * Constructs an appropriate exception with an appropriate message.
     * 
     * @param name
     *            not null
     * @param e
     *            not null
     * @return not null
     */
    protected MailetException loadFailed(String name, String type, Exception e) {
        return new MailetException("Could not load " + type + " (" + name + ")", e);
    }

    /**
     * Return the package name which will be used as suffix if the name provided
     * for {@link #load(String)} does not contain a package name
     * 
     * @return stdPackage
     */
    protected abstract String getStandardPackage();

}
