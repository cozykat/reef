/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.messaging.javaclient

import org.totalgrid.reef.messaging._
import org.totalgrid.reef.messaging.sync._
import org.totalgrid.reef.broker._
import org.totalgrid.reef.broker.qpid.QpidBrokerConnection

import org.totalgrid.reef.reactor.ReactActor

import org.totalgrid.reef.japi.client.{ ConnectionListener, Connection, Session, SessionExecutionPool }
import org.totalgrid.reef.api.ServiceList
import org.totalgrid.reef.api.scalaclient.ClientSession

/**
 * A bridge for easily mapping the Scala messaging constructs onto Java constructs
 */
class AMQPConnection(config: BrokerConnectionInfo, servicesList: ServiceList, timeoutms: Long) extends Connection {

  /// Scala factory class we're wrapping to simplify access to java clients
  private val factory = new AMQPSyncFactory with ReactActor {
    val broker = new QpidBrokerConnection(config)

    // shim to get SessionPool structural typing happy
    def getClientSession(): ClientSession = new ProtoClient(this, servicesList, timeoutms)
  }

  final override def addConnectionListener(listener: ConnectionListener) =
    factory.addConnectionListener(listener)

  final override def removeConnectionListener(listener: ConnectionListener) =
    factory.removeConnectionListener(listener)

  final override def connect(timeoutMs: Long) = factory.connect(timeoutMs)

  final override def start() = factory.start()

  final override def disconnect(timeoutMs: Long) = factory.disconnect(timeoutMs)

  final override def stop() = factory.stop()

  final override def newSession(): Session =
    new SessionWrapper(new ProtoClient(factory, servicesList, timeoutms))

  final override def newSessionPool(): SessionExecutionPool = new SessionPool(factory)

}

