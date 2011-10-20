/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.broker.qpid

import java.lang.Boolean
import org.totalgrid.reef.api.sapi.config.impl.FileConfigReader
import org.totalgrid.reef.api.sapi.config.ConfigReader

object QpidBrokerConnectionInfo {

  def loadInfo(configReader: ConfigReader): QpidBrokerConnectionInfo = QpidBrokerProperties.get(configReader)

  def loadInfo(fileName: String): QpidBrokerConnectionInfo = loadInfo(new FileConfigReader(fileName))
}

class QpidBrokerConnectionInfo(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val virtualHost: String,
    val ssl: Boolean = false,
    val trustStore: String = "",
    val trustStorePassword: String = "",
    val keyStore: String = "",
    val keyStorePassword: String = "") {

  override def toString() = {
    if (ssl) {
      "amqps:/" + user + "@" + host + ":" + port + "/" + virtualHost + "{" + trustStore + "," + keyStore + "}"
    } else {
      "amqp:/" + user + "@" + host + ":" + port + "/" + virtualHost
    }

  }
}