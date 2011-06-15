/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.frontend

import org.totalgrid.reef.proto.FEP.{ CommEndpointConnection => ConnProto }
import org.totalgrid.reef.proto.FEP.CommChannel
import org.totalgrid.reef.messaging.Connection

import scala.collection.JavaConversions._
import org.totalgrid.reef.util.Conversion.convertIterableToMapified

import org.totalgrid.reef.protocol.api._
import org.totalgrid.reef.sapi._
import org.totalgrid.reef.proto.Model.ReefUUID
import org.totalgrid.reef.proto.Measurements.MeasurementBatch
import org.totalgrid.reef.broker.CloseableChannel
import org.totalgrid.reef.japi.{ Envelope, ReefServiceException }

// Data structure for handling the life cycle of connections
class FrontEndConnections(comms: Seq[Protocol], conn: Connection) extends KeyedMap[ConnProto] {

  def getKey(c: ConnProto) = c.getUid

  val protocols = comms.mapify { _.name }

  var commandAdapters = Map.empty[String, CloseableChannel]

  val pool = conn.getSessionPool

  private def getProtocol(name: String): Protocol = protocols.get(name) match {
    case Some(p) => p
    case None => throw new IllegalArgumentException("Unknown protocol: " + name)
  }

  def hasChangedEnoughForReload(updated: ConnProto, existing: ConnProto) = {
    updated.hasRouting != existing.hasRouting ||
      (updated.hasRouting && updated.getRouting.getServiceRoutingKey != existing.getRouting.getServiceRoutingKey)
  }

  def addEntry(c: ConnProto) = {

    val protocol = getProtocol(c.getEndpoint.getProtocol)
    val endpoint = c.getEndpoint
    val port = c.getEndpoint.getChannel

    val publisher = new OrderedPublisher(pool)

    val batchPublisher = newMeasBatchPublisher(publisher, c.getRouting.getServiceRoutingKey)
    val channelListener = newChannelListener(port.getUuid)
    val endpointListener = newEndpointListener(c.getUid)

    // add the device, get the command issuer callback
    if (protocol.requiresChannel) protocol.addChannel(port, channelListener)
    val cmdHandler = protocol.addEndpoint(endpoint.getName, port.getName, endpoint.getConfigFilesList.toList, batchPublisher, endpointListener)
    val service = conn.bindService(new SingleEndpointCommandService(cmdHandler), AddressableDestination(c.getRouting.getServiceRoutingKey))

    commandAdapters += c.getEndpoint.getName -> service

    logger.info("Added endpoint " + c.getEndpoint.getName + " on protocol " + protocol.name + " routing key: " + c.getRouting.getServiceRoutingKey)
  }

  def removeEntry(c: ConnProto) {
    logger.debug("Removing endpoint " + c.getEndpoint.getName)
    val protocol = getProtocol(c.getEndpoint.getProtocol)

    // need to make sure we close the addressable service
    commandAdapters.get(c.getEndpoint.getName) match {
      case Some(serviceBinding) => serviceBinding.close
      case None =>
    }
    commandAdapters -= c.getEndpoint.getName

    protocol.removeEndpoint(c.getEndpoint.getName)
    if (protocol.requiresChannel) protocol.removeChannel(c.getEndpoint.getChannel.getName)
    logger.info("Removed endpoint " + c.getEndpoint.getName + " on protocol " + protocol.name)
  }

  private def newMeasBatchPublisher(publisher: OrderedPublisher, routingKey: String) =
    new OrderedPublisherAdapter[MeasurementBatch](publisher, Envelope.Verb.POST, AddressableDestination(routingKey), 1)(x => x)

  private def newEndpointListener(connectionUid: String) = new Listener[ConnProto.State] {

    override def onUpdate(state: ConnProto.State) = {
      val update = ConnProto.newBuilder.setUid(connectionUid).setState(state).build
      try {
        val result = pool.borrow { _.post(update).await().expectOne }
        logger.info("Updated connection state: " + result)
      } catch {
        case ex: ReefServiceException => logger.error("Exception while updating endpoint comm state", ex)
      }
    }
  }

  private def newChannelListener(channelUid: ReefUUID) = new Listener[CommChannel.State] {

    override def onUpdate(state: CommChannel.State) = {
      val update = CommChannel.newBuilder.setUuid(channelUid).setState(state).build
      try {
        val result = pool.borrow { _.post(update).await().expectOne }
        logger.info("Updated channel: " + result)
      } catch {
        case ex: ReefServiceException => logger.error("Exception while updating comm channel state", ex)
      }
    }

  }

}

