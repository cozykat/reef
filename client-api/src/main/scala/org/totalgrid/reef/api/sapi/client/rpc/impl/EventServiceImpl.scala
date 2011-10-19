package org.totalgrid.reef.api.sapi.client.rpc.impl

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

import org.totalgrid.reef.api.proto.Events.EventSelect

import scala.collection.JavaConversions._

import org.totalgrid.reef.api.sapi.client.rpc.EventService
import org.totalgrid.reef.api.sapi.impl.Descriptors
import org.totalgrid.reef.api.japi.client.rpc.impl.builders.{ EventRequestBuilders, EventListRequestBuilders }
import org.totalgrid.reef.api.sapi.client.rpc.framework.HasAnnotatedOperations

trait EventServiceImpl extends HasAnnotatedOperations with EventService {

  override def getEvent(uid: String) = ops.operation("Couldn't get event with uid: " + uid) {
    _.get(EventRequestBuilders.getByUID(uid)).map(_.one)
  }

  override def getRecentEvents(limit: Int) = ops.operation("Couldn't get the last " + limit + " recent events") {
    _.get(EventListRequestBuilders.getAll(limit)).map(_.one.map(_.getEventsList.toList))
  }

  override def subscribeToRecentEvents(limit: Int) = {
    ops.subscription(Descriptors.event, "Couldn't subscribe to recent events") { (sub, client) =>
      client.get(EventListRequestBuilders.getAll(limit), sub).map(_.one.map(_.getEventsList.toList))
    }
  }

  override def subscribeToRecentEvents(types: List[String], limit: Int) = {
    ops.subscription(Descriptors.event, "Couldn't subscribe to recent events") { (sub, client) =>
      client.get(EventListRequestBuilders.getAllByEventTypes(types, limit), sub).map(_.one.map(_.getEventsList.toList))
    }
  }

  override def getRecentEvents(types: List[String], limit: Int) = {
    ops.operation("Couldn't get recent events with types: " + types) {
      _.get(EventListRequestBuilders.getAllByEventTypes(types, limit)).map(_.one.map(_.getEventsList.toList))
    }
  }

  override def getEvents(selector: EventSelect) = ops.operation("Couldn't get events matching: " + selector) {
    _.get(EventListRequestBuilders.getByEventSelect(selector)).map(_.one.map(_.getEventsList.toList))
  }

  override def subscribeToEvents(selector: EventSelect) = {
    ops.subscription(Descriptors.event, "Couldn't subscribe to events matching: " + selector) { (sub, client) =>
      client.get(EventListRequestBuilders.getByEventSelect(selector), sub).map(_.one.map(_.getEventsList.toList))
    }
  }

}