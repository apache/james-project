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

package org.apache.james.util.docker;

public interface Images {
    String FAKE_SMTP = "quanth99/rest-smtp-sink:1.0"; // Original Dockerfile: https://github.com/ambled/rest-smtp-sink/blob/master/Dockerfile
    String RABBITMQ = "rabbitmq:4.1.1-management";
    String ELASTICSEARCH_2 = "elasticsearch:2.4.6";
    String ELASTICSEARCH_6 = "docker.elastic.co/elasticsearch/elasticsearch:6.3.2";
    String OPENSEARCH = "opensearchproject/opensearch:2.19.2";
    String TIKA = "apache/tika:3.2.0.0";
    String MOCK_SMTP_SERVER = "linagora/mock-smtp-server:0.7";
    String OPEN_LDAP = "osixia/openldap:1.5.0";
}
