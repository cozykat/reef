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
package org.totalgrid.reef.services.core

import org.totalgrid.reef.services.framework._

import org.totalgrid.reef.models.{ ApplicationInstance, ApplicationSchema }

import org.totalgrid.reef.messaging.AMQPProtoFactory

import org.totalgrid.reef.models._
import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.proto.ProcessStatus._
import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.executor.Executor
import org.totalgrid.reef.services.ProtoServiceCoordinator
import org.totalgrid.reef.japi.Envelope

class ProcessStatusCoordinator(trans: ServiceTransactable[ProcessStatusServiceModel], contextSource: RequestContextSource) extends ProtoServiceCoordinator with Logging {

  def startTimeoutChecks(react: Executor) {
    // we need to delay the timeout check a bit to make sure any already queued heartbeat messages are waiting
    // to be processed. If we checked the timeouts before processing all waiting messages we would always disable 
    // all applications if this coordinator had been turned off for longer than periodMs even if the other apps
    // had been sending heartbeats the whole.
    // TODO: implement a "sentinal" callback for when all pending messages processed on a queue
    react.delay(10000) {
      react.repeat(10000) {
        try {
          checkTimeouts(System.currentTimeMillis)
        } catch {
          case e: Exception => logger.error("Error checking timeout", e)
        }
      }
    }
  }

  def addAMQPConsumers(amqp: AMQPProtoFactory, reactor: Executor) {
    val sub = amqp.listen("proc_status_queue", "proc_status", StatusSnapshot.parseFrom(_), { (x: StatusSnapshot) => reactor.execute { handleRawStatus(x) } })
    sub.observeConnection((online: Boolean) => {
      if (online) {
        startTimeoutChecks(reactor)
      }
    })
  }

  def checkTimeouts(now: Long) {
    contextSource.transaction { context =>
      trans.transaction { model =>

        ApplicationSchema.heartbeats.where(h => h.isOnline === true and (h.timeoutAt lte now)).foreach(h => {
          logger.info("App " + h.instanceName.value + ": has timed out at " + now + " (" + (h.timeoutAt - now) + ")")
          model.takeApplicationOffline(context, h, now)
        })
      }
    }
  }

  def handleRawStatus(ss: StatusSnapshot): Unit = {

    def update(context: RequestContext, model: ProcessStatusServiceModel, hbeat: HeartbeatStatus) {
      if (hbeat.isOnline) {
        if (ss.getOnline) {
          logger.info("Got heartbeat for: " + ss.getInstanceName + ": " + ss.getProcessId + " by " + (hbeat.timeoutAt - ss.getTime))
          hbeat.timeoutAt = ss.getTime + hbeat.periodMS * 2
          // don't publish a modify
          ApplicationSchema.heartbeats.update(hbeat)
        } else {
          logger.info("App " + hbeat.instanceName.value + ": is shutting down at " + ss.getTime)
          model.takeApplicationOffline(context, hbeat, ss.getTime)
        }
      } else {
        logger.warn("App " + ss.getInstanceName + ": is marked offline but got message!")
      }
    }
    contextSource.transaction { context =>
      trans.transaction { model =>

        if (!ss.hasProcessId) {
          logger.warn("Malformed" + ss.getInstanceName + ": isn't configured!")
        } else {
          ApplicationSchema.heartbeats.where(_.processId === ss.getProcessId).toList match {
            case List(hbeat) => update(context, model, hbeat)
            case _ => logger.warn("App " + ss.getInstanceName + ": isn't configured, processId: " + ss.getProcessId)
          }
        }
      }
    }
  }
}

/*

class ProcessStatusCoordinator(publishers: ServiceEventPublishers) extends Logging {

  val coordinatorModel = new ProcessStatusModel(publishers.getEventSink(classOf[StatusSnapshot]))

  var reactor: Option[Executor] = None

  def addProcesses(react: Executor) {
    reactor = Some(react)
  }

  def startTimeoutChecks(react: Executor) {
    // we need to delay the timeout check a bit to make sure any already queued heartbeat messages are waiting
    // to be processed. If we checked the timeouts before processing all waiting messages we would always disable 
    // all applications if this coordinator had been turned off for longer than periodMs even if the other apps
    // had been sending heartbeats the whole.
    // TODO: implement a "sentinal" callback for when all pending messages processed on a queue
    react.delay(10000) {
      react.repeat(10000) {
        try {
          checkTimeouts(System.currentTimeMillis)
        } catch {
          case e: Exception => logger.error("Error starting timeout checks", e)
        }
      }
    }
  }

  def addAMQPConsumers(amqp: AMQPProtoFactory) {
    val sub = amqp.listen("proc_status_queue", "proc_status", StatusSnapshot.parseFrom(_), handleRawStatus _)
    sub.observeConnection((online: Boolean) => {
      if (online && reactor.isDefined) {
        startTimeoutChecks(reactor.get)
        reactor = None
      }
    })
  }

  def checkTimeouts(now: Long) {
    coordinatorModel.transaction {
      ApplicationSchema.heartbeats.where(h => h.isOnline === true and (h.timeoutAt lte now)).foreach(h => {
        coordinatorModel.takeApplicationOffline(h, now)
      })
    }
  }

  def handleRawStatus(ss: StatusSnapshot): Unit = {

    def update(hbeat: HeartbeatStatus) {
      if (hbeat.isOnline) {
        if (ss.getOnline) {
          info { "Got heartbeat for: " + ss.getInstanceName + ": " + ss.getUuid + " by " + (hbeat.timeoutAt - ss.getTime) }
          hbeat.timeoutAt = ss.getTime + hbeat.periodMS * 2
          // don't publish a modify
          ApplicationSchema.heartbeats.update(hbeat)
        } else {
          coordinatorModel.takeApplicationOffline(hbeat, ss.getTime)
        }
      } else {
        warn { "App " + ss.getInstanceName + ": is marked offline but got message!" }
      }
    }

    coordinatorModel.transaction {
      if (!ss.hasUuid) {
        warn { "Malformed" + ss.getInstanceName + ": isn't configured!" }
      } else {
        ApplicationSchema.heartbeats.lookup(ss.getUuid.toLong) match {
          case Some(hbeat) => update(hbeat)
          case None => logger.warn("App " + ss.getInstanceName + ": isn't configured!")
        }
      }
    }
  }
}
*/
