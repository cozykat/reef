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
package org.totalgrid.reef.protocol.api

import org.totalgrid.reef.proto.{ FEP, Model }
import scala.collection.immutable

import org.totalgrid.reef.util.Logging

trait BaseProtocol extends IProtocol with Logging {

  case class Endpoint(name: String, channel: Option[FEP.Port], config: List[Model.ConfigFile], listener: IEndpointListener) /// The issue function and the channel
  case class Channel(config: FEP.Port, listener: IChannelListener)

  // only mutable state is current assignment of these variables
  private var endpoints = immutable.Map.empty[String, Endpoint] /// maps uids to a Endpoint
  private var channels = immutable.Map.empty[String, Channel] /// maps uids to a Port

  override def addChannel(p: FEP.Port, listener: IChannelListener): Unit = {
    channels.get(p.getName) match {
      case None =>
        channels = channels + (p.getName -> Channel(p, listener))
        _addChannel(p, listener)
      case Some(x) =>
        if (x == p) info("Ignoring duplicate channel " + p)
        else throw new IllegalArgumentException("Port with that name already exists: " + p)
    }
  }

  override def addEndpoint(endpoint: String, channelName: String, config: List[Model.ConfigFile], publish: IPublisher, listener: IEndpointListener): ICommandHandler = {

    endpoints.get(endpoint) match {
      case Some(x) => throw new IllegalArgumentException("Endpoint already exists: " + endpoint)
      case None =>
        channels.get(channelName) match {
          case Some(p) =>
            endpoints += endpoint -> Endpoint(endpoint, Some(p.config), config, listener)
            _addEndpoint(endpoint, channelName, config, publish, listener)
          case None =>
            if (requiresChannel) throw new IllegalArgumentException("Port not registered " + channelName)
            endpoints += endpoint -> Endpoint(endpoint, None, config, listener)
            _addEndpoint(endpoint, channelName, config, publish, listener)
        }
    }
  }

  override def removeChannel(channel: String): Unit = {
    channels.get(channel) match {
      case Some(p) =>
        endpoints.values.filter { e => // if a channel is removed, remove all devices on that channel first
          e.channel match {
            case Some(x) => x.getName == channel
            case None => false
          }
        }.foreach { e => removeEndpoint(e.name) }
        channels -= channel
        _removeChannel(channel)
      case None =>
        throw new IllegalArgumentException("Cannot remove unknown channel " + channel)
    }
  }

  /// remove the device from the map and its channel's device list
  override def removeEndpoint(endpoint: String): Unit = {
    endpoints.get(endpoint) match {
      case Some(Endpoint(name, _, _, _)) =>
        endpoints -= name
        _removeEndpoint(name)
      case None =>
        throw new IllegalArgumentException("Cannot remove unknown endpoint " + endpoint)
    }
  }

  /// These get implemented by the parent
  protected def _addChannel(p: FEP.Port, listener: IChannelListener)
  protected def _removeChannel(channel: String)
  protected def _addEndpoint(endpoint: String, channel: String, config: List[Model.ConfigFile], publish: IPublisher, listener: IEndpointListener): ICommandHandler
  protected def _removeEndpoint(endpoint: String)

}
