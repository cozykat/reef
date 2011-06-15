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
package org.totalgrid.reef.japi.request.impl

import scala.collection.JavaConversions._
import org.totalgrid.reef.proto.Alarms.{ Alarm, EventConfig }
import org.totalgrid.reef.japi.request.EventConfigService

trait EventConfigServiceImpl extends ReefServiceBaseClass with EventConfigService {

  override def getAllEventConfigurations = ops("Couldn't get all event configs") {
    _.get(EventConfig.newBuilder.setEventType("*").build).await().expectMany
  }

  override def getEventConfiguration(eventType: String) = ops("Couldn't get event config with type: " + eventType) {
    _.get(EventConfig.newBuilder.setEventType(eventType).build).await().expectOne
  }

  override def setEventConfig(eventType: String, severity: Int, designation: EventConfig.Designation, audibleAlarm: Boolean, resourceString: String) = {
    ops("Couldn't create event config with type: " + eventType) { session =>

      val alarmState = if (audibleAlarm) Alarm.State.UNACK_AUDIBLE else Alarm.State.UNACK_SILENT

      val b = EventConfig.newBuilder.setEventType(eventType)
        .setSeverity(severity)
        .setDesignation(designation)
        .setResource(resourceString)
        .setAlarmState(alarmState)

      session.put(b.build).await().expectOne
    }
  }

  override def deleteEventConfig(config: EventConfig) = ops("Couldn't delete event config with type: " + config.getEventType) {
    _.delete(config).await().expectOne
  }
}