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
package org.totalgrid.reef.client.sapi.client.factory

import org.totalgrid.reef.broker.qpid.QpidBrokerConnectionFactory
import org.totalgrid.reef.client.settings.AmqpSettings
import net.agileautomata.executor4s.Executors
import org.totalgrid.reef.client.sapi.client.rest.Connection
import org.totalgrid.reef.broker.BrokerConnection
import org.totalgrid.reef.client.sapi.client.rest.impl.DefaultConnection
import org.totalgrid.reef.client.ServicesList

/**
 * provide a one-shot factory that holds the made connection and calls disconnect
 * during shutdown. Not thread-safe and needs to be reconstructed for a new
 * connection.
 */
class ReefFactory(amqpSettings: AmqpSettings, servicesList: ServicesList) {
  private val factory = new QpidBrokerConnectionFactory(amqpSettings)
  private val exe = Executors.newScheduledThreadPool(5)

  private var broker = Option.empty[BrokerConnection]
  private var connection = Option.empty[Connection]

  /**
   * make a connection or return the already prepared connection
   */
  def connect(): Connection = {
    if (connection.isEmpty) {
      broker = Some(factory.connect)
      connection = Some(new DefaultConnection(broker.get, exe, 5000))
      connection.get.addServicesList(servicesList)
    }
    connection.get
  }

  /**
   * disconnect the connection and start shutting down the executor
   */
  def shutdown() {
    // TODO: should this be connection.disconnect?
    broker.foreach { _.disconnect }
    exe.shutdown()
  }

  /**
   * calls shutdown, then waits for the executor to terminate
   * NOTE: do not call from inside of the executor (such as in an onDisconnect callback)
   * since it will deadlock in terminate
   */
  def terminate() {
    shutdown()
    exe.terminate()
  }
}