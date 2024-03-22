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
package org.apache.james.mailbox.jpa.mail.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "UserFlag")
@Table(name = "JAMES_MAIL_USERFLAG")
public class JPAUserFlag {


    /** The system unique key */
    @Id
    @GeneratedValue
    @Column(name = "USERFLAG_ID", nullable = true)
    private long id;
    
    /** Local part of the name of this property */
    @Basic(optional = false)
    @Column(name = "USERFLAG_NAME", nullable = false, length = 500)
    private String name;
    
    
    /**
     * @deprecated enhancement only
     */
    @Deprecated 
    public JPAUserFlag() {

    }
    
    /**
     * Constructs a User Flag.
     * @param name not null
     */
    public JPAUserFlag(String name) {
        super();
        this.name = name;
    }

    /**
     * Constructs a User Flag, cloned from the given.
     * @param flag not null
     */
    public JPAUserFlag(JPAUserFlag flag) {
        this(flag.getName());
    }

  
    
    /**
     * Gets the name.
     * @return not null
     */
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (id ^ (id >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JPAUserFlag other = (JPAUserFlag) obj;
        if (id != other.id) {
            return false;
        }
        return true;
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation 
     * of this object.
     */
    public String toString() {
        return "JPAUserFlag ( "
            + "id = " + this.id + " "
            + "name = " + this.name
            + " )";
    }
    
}
