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
package org.totalgrid.reef.messaging

import scala.collection.immutable.Queue
import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.reactor.{ Reactor, Lifecycle }

/** Keeps the connection to qpid up. Notifies linked AMQPSessionHandler 
 */
trait AMQPConnectionReactor extends Reactor with Lifecycle
    with BrokerConnectionListener with Logging {

  /// must be defined in concrete class
  protected val broker: BrokerConnection

  /** Add a session handler to the connection. If the actor is already connected, 
   * 	the session handler will be notified immediately.
   *   
   *	@param handler class that will receive new session notifications 	
   */
  def add[T <: ChannelObserver](handler: T): T = {
    execute { addChannelObserver(handler) }
    handler
  }

  def addConnectionListener(listener: BrokerConnectionListener): Unit =
    listeners = listeners.enqueue(listener)

  def getChannel(): BrokerChannel = broker.newBrokerChannel()

  /// mutable state
  private var listeners = Queue.empty[BrokerConnectionListener]
  private var queue = Queue.empty[ChannelObserver]
  private var reconnectOnClose = true

  override def afterStart() = {
    broker.setConnectionListener(Some(this))
    reconnectOnClose = true
    this.reconnect()
  }

  /// overriders base class. Terminates all the connections and machinery
  override def beforeStop() = {
    reconnectOnClose = false
    broker.close()
  }

  private def addChannelObserver(handler: ChannelObserver) {
    queue = queue.enqueue(handler)
    if (broker.isConnected) createChannel(handler)
  }

  // helper for starting a new connection chain
  private def reconnect() = execute { connect(1000) }

  /// Makes a connection attempt. Retries if with exponential backoff
  /// if the attempt fails
  private def connect(retryms: Long): Unit = {
    try {
      broker.connect()
      queue.foreach { createChannel(_) }
      //listeners.foreach { _.opened() }
    } catch {
      case t: Throwable =>
        error(t)
        // if we fail, retry, use exponential backoff
        delay(retryms) { connect(2 * retryms) }
    }
  }

  /// gives a broker object its session. May fail.
  private def createChannel(co: ChannelObserver) = {
    try {
      co.online(broker.newBrokerChannel())
      debug("Added channel for type: " + co.getClass)
    } catch {
      case ex: Exception => error("error configuring sessions: ", ex)
    }
  }

  /* --- Implement Broker Connection Listener --- */

  override def closed() {
    info(" Connection closed")
    if (reconnectOnClose) this.delay(1000) { reconnect() }
    queue.foreach { a => a.offline() }
    listeners.foreach { _.closed() }
  }

  override def opened() = {
    info("Connection opened")
    listeners.foreach { _.opened() }
  }

}
