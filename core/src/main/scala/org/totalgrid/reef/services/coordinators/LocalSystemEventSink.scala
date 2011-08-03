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
package org.totalgrid.reef.services.coordinators

import org.totalgrid.reef.event.SystemEventSink
import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.services.core.EventServiceModelFactory
import org.totalgrid.reef.japi.ReefServiceException
import org.squeryl.PrimitiveTypeMode
import org.totalgrid.reef.services.framework.{ OperationBuffer, BasicServiceTransactable, SimpleRequestContext }

class LocalSystemEventSink extends SystemEventSink with Logging {

  private var eventModelFactory: Option[EventServiceModelFactory] = None

  def publishSystemEvent(evt: org.totalgrid.reef.proto.Events.Event) {
    try {
      // we need a different transaction so events are retained even if
      // we rollback the rest of the transaction because of an error
      PrimitiveTypeMode.transaction {
        val context = new SimpleRequestContext
        BasicServiceTransactable.doTransaction(context.events, { buffer: OperationBuffer =>
          eventModelFactory.get.transaction {

            // notice we are skipping the event service preCreate step that strips time and userId
            // because our local trusted service components have already set those values correctly
            _.createFromProto(context, evt)

          }
        })
      }
    } catch {
      case e: ReefServiceException =>
        logger.warn("Service Exception thunking event: " + e.getMessage)
    }
  }

  def setEventModel(factory: EventServiceModelFactory) {
    eventModelFactory = Some(factory)
  }
}