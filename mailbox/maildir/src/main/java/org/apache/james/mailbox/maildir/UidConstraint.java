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
package org.apache.james.mailbox.maildir;

import java.util.LinkedList;

public class UidConstraint {
    
    private final LinkedList<Constraint> constraints = new LinkedList<Constraint>();
    
    public UidConstraint append(Constraint constraint) {
        constraints.add(constraint);
        return this;
    }
    
    public UidConstraint equals(long uid) {
        constraints.add(new Equals(uid));
        return this;
    }
    
    public UidConstraint lessOrEquals(long uid) {
        constraints.add(new LessOrEquals(uid));
        return this;
    }
    
    public UidConstraint greaterOrEquals(long uid) {
        constraints.add(new GreaterOrEquals(uid));
        return this;
    }
    
    public UidConstraint between(long lower, long upper) {
        constraints.add(new Between(lower, upper));
        return this;
    }
    
    public boolean isAllowed(long uid) {
        return constraints.stream()
            .allMatch(constraint -> constraint.isAllowed(uid));
    }

    public abstract static class Constraint {
        
        public abstract boolean isAllowed(long uid);
        
    }
    
    public static class Equals extends Constraint {

        private final long uid;
        
        public Equals(long uid) {
            this.uid = uid;
        }
        
        @Override
        public boolean isAllowed(long uid) {
            return this.uid == uid;
        }
        
    }
    
    public static class LessOrEquals extends Constraint {

        private final long uid;
        
        public LessOrEquals(long uid) {
            this.uid = uid;
        }

        @Override
        public boolean isAllowed(long uid) {
            return uid <= this.uid;
        }
        
    }
    
    public static class GreaterOrEquals extends Constraint {

        private final long uid;
        
        public GreaterOrEquals(long uid) {
            this.uid = uid;
        }

        @Override
        public boolean isAllowed(long uid) {
            return uid >= this.uid;
        }
        
    }
    
    public static class Between extends Constraint {
        
        private final long lower;
        private final long upper;
        
        public Between(long lower, long upper) {
            this.lower = lower;
            this.upper = upper;
        }

        @Override
        public boolean isAllowed(long uid) {
            return (uid >= lower) && (uid <= upper);
        }
    }
    
}
