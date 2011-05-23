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
package org.totalgrid.reef.messaging.qpid

import scala.{ Option => ScalaOption }

import org.apache.qpid.transport.{ Connection }
import org.apache.qpid.transport.{ ConnectionListener, ConnectionException }

import org.totalgrid.reef.util.Logging

import org.totalgrid.reef.messaging.{ BrokerConnectionInfo, BrokerConnection, BrokerChannel }

class QpidBrokerConnection(config: BrokerConnectionInfo) extends BrokerConnection with ConnectionListener with Logging {

  private var connection: ScalaOption[Connection] = None

  override def toString() = config.toString

  final override def doConnect() = connection match {
    case Some(c) => true
    case None =>
      val conn = new Connection
      conn.addConnectionListener(this)
      info("Connecting to " + config)
      try {
        conn.connect(config.host, config.port, config.virtualHost, config.user, config.password, false)
        true
      } catch {
        case ex: Exception =>
          error(ex)
          false
      }
  }

  final override def doDisconnect(): Boolean = connection match {
    case Some(c) =>
      unlinkChannels()
      c.close()
      connection = None
      true
    case None =>
      true
  }

  private def unlinkChannels() = {
    channels.foreach(_.stop())
    channels = Nil
  }

  /* -- Implement Qpid Connection Listener -- */

  def closed(conn: Connection) {
    info("Qpid Connection closed")
    connection = None
    this.setClosed()
    unlinkChannels()
  }

  def opened(conn: Connection) {
    info("Qpid Connection opened")
    connection = Some(conn)
    this.setOpen()
  }

  def exception(conn: Connection, ex: ConnectionException) {
    error("Connection Exception: ", ex)
  }

  /* -- End Qpid Connection Listener -- */

  private var channels = List.empty[QpidBrokerInterface]

  final override def newBrokerChannel(): BrokerChannel = connection match {
    case Some(c) =>
      val channel = new QpidBrokerInterface(c.createSession(0))
      channels = channel :: channels
      channel
    case None =>
      throw new Exception("Connection is closed, cannot create channel")
  }

}
