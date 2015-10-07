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
package org.apache.james.util.concurrent;

public interface JMXEnabledThreadPoolExecutorMBean {

    /**
     * Return the active threads
     * 
     * @return aThreads
     */
    int getActiveThreads();

    /**
     * Return the active Tasks
     * 
     * @return aTasks
     */
    int getActiveTasks();

    /**
     * Return the total tasks handled by this executor
     * 
     * @return tTasks
     */
    int getTotalTasks();

    /**
     * Return the queued tasks
     * 
     * @return qTasks
     */
    int getQueuedTasks();

    /**
     * Return the average time for a task (in ms)
     * 
     * @return aTime
     */
    double getAverageTaskTime();
    
    /**
     * Return the maximal allowed count of threads
     * 
     * @return maxThreads
     */
    int getMaximalThreads();
}
