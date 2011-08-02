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

import org.totalgrid.reef.services.core.{ CommunicationEndpointConnectionServiceModel, MeasurementProcessingConnectionServiceModel }
import org.totalgrid.reef.measurementstore.MeasurementStore
import org.totalgrid.reef.proto.FEP.CommEndpointConnection
import org.totalgrid.reef.models._

import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.services.framework.{ RequestContext, LinkedBufferedEvaluation }

class SquerylBackedMeasurementStreamCoordinator(
  measProcModel: MeasurementProcessingConnectionServiceModel,
  fepConnection: CommunicationEndpointConnectionServiceModel,
  val measurementStore: MeasurementStore)
    extends MeasurementCoordinationQueries
    with CommunicationEndpointOfflineBehaviors
    with LinkedBufferedEvaluation
    with MeasurementStreamCoordinator {

  val initialConnectionState = CommEndpointConnection.State.COMMS_DOWN.getNumber
  val onlineState = CommEndpointConnection.State.COMMS_UP.getNumber

  val measProcTable = ApplicationSchema.measProcAssignments
  val fepAssignmentTable = ApplicationSchema.frontEndAssignments

  def onEndpointCreated(context: RequestContext, ce: CommunicationEndpoint) {
    val now = System.currentTimeMillis

    val measProcId = getMeasProc().map { _.id }
    val measProcAssignedTime = measProcId.map { x => now }
    val serviceRoutingKey = measProcId.map { x => "meas_batch_" + ce.entityName }

    // we always mark new endpoints offline to start
    val offlineTime = Some(now)
    markOffline(ce)

    measProcModel.create(context, new MeasProcAssignment(ce.id, serviceRoutingKey, measProcId, measProcAssignedTime, None))
    fepConnection.create(context, new FrontEndAssignment(ce.id, initialConnectionState, true, None, None, None, offlineTime, None))
  }

  def onEndpointUpdated(context: RequestContext, ce: CommunicationEndpoint) {
    val measProcAssignment = measProcTable.where(measProc => measProc.endpointId === ce.id).single
    checkMeasProcAssignment(context, measProcAssignment)

    // when there is any change, delete the current assignment
    val fepProcAssignment = fepAssignmentTable.where(fep => fep.endpointId === ce.id).single
    fepConnection.delete(context, fepProcAssignment)

    // then either assign the endpoint to a compatible FEP or no FEP
    val assigned = determineFepAssignment(fepProcAssignment.copy(applicationId = None), ce)
    fepConnection.create(context, assigned.getOrElse(new FrontEndAssignment(ce.id, initialConnectionState, fepProcAssignment.enabled, None, None, None, Some(System.currentTimeMillis), None)))
  }

  def onEndpointDeleted(context: RequestContext, ce: CommunicationEndpoint) {
    val assignedMeasProc = measProcTable.where(measProc => measProc.endpointId === ce.id).single
    measProcModel.delete(context, assignedMeasProc)

    val assignedFep = fepAssignmentTable.where(fep => fep.endpointId === ce.id).single
    fepConnection.delete(context, assignedFep)
  }

  def onFepAppChanged(context: RequestContext, app: ApplicationInstance, added: Boolean) {

    val rechecks = if (added) {
      fepAssignmentTable.where(fep => fep.applicationId.isNull).toList
    } else {
      fepAssignmentTable.where(fep => fep.applicationId === app.id).toList
    }
    logger.info("FEP: " + app.instanceName + " added: " + added + " rechecking: " + rechecks.map { _.endpoint.value.get.entityName })
    rechecks.foreach { a => checkFepAssignment(context, a, a.endpoint.value.get) }
  }

  /**
   * determine the least loaded FEP to talk to our endpoint
   */
  private def determineFepAssignment(assign: FrontEndAssignment, ce: CommunicationEndpoint): Option[FrontEndAssignment] = {

    // if the measuremntProcessor has collected all its resources we can assign an fep by giving it a service routing key
    val measAssign: MeasProcAssignment = ce.measProcAssignment.value
    val serviceRoutingKey = measAssign.readyTime.flatMap { x => measAssign.serviceRoutingKey }

    // lookup a compatible FEP only if the connection is enabled
    val applicationId = if (assign.enabled) getFep(ce).map { _.id } else None

    logger.info(
      ce.entityName + " assigned FEP: " + applicationId + " protocol: " + ce.protocol +
        " port: " + ce.port.value + " routingKey: " + serviceRoutingKey + " last Fep:" + assign.applicationId)

    val assignedTime = applicationId.map { x => System.currentTimeMillis }
    if (assign.applicationId != applicationId || assign.serviceRoutingKey != serviceRoutingKey) {
      val now = System.currentTimeMillis
      markOffline(ce)
      val newAssign = assign.copy(
        applicationId = applicationId,
        assignedTime = assignedTime,
        offlineTime = Some(now),
        onlineTime = None,
        serviceRoutingKey = serviceRoutingKey)
      Some(newAssign)
    } else {
      None
    }
  }

  /**
   * checks the fep assignment and sends out an update if there was a change
   */
  private def checkFepAssignment(context: RequestContext, assign: FrontEndAssignment, ce: CommunicationEndpoint) {
    val newAssign = determineFepAssignment(assign, ce)
    // update the fep assignment if it changed
    newAssign.foreach(newAssignment => fepConnection.update(context, newAssignment, assign))
  }

  def onFepConnectionChange(context: RequestContext, sql: FrontEndAssignment, existing: FrontEndAssignment) {

    val endpoint = sql.endpoint.value.get
    endpoint.entity.value // preload LazyVar entity since it may be deleted by the time event is rendered

    val online = sql.state == onlineState
    if (online) {
      if (existing.onlineTime == None && sql.onlineTime != None) {
        markOnline(endpoint)
      }
    } else {
      // Workaround: can't check offlineTime because assignment may have reset it. Just do it twice.
      markOffline(endpoint)
    }
    if (sql.enabled != existing.enabled) {
      checkFepAssignment(context, sql, endpoint)
    }
  }

  /**
   * whenever the meas proc table is updated we want to sure we re-evaluate the fep assignments
   * to either enable or disable them
   */
  def onMeasProcAssignmentChanged(context: RequestContext, meas: MeasProcAssignment) {
    logger.info("MeasProc Change, rechecking: " + meas.endpoint.value.map { _.entityName } + " readyTime: " + meas.readyTime + " key: " + meas.serviceRoutingKey)
    fepAssignmentTable.where(fep => fep.endpointId === meas.endpointId).headOption.foreach { assign =>
      checkFepAssignment(context, assign, assign.endpoint.value.get)
    }
  }

  /**
   * when a new measurement processor is added or removed we want to check all of the meas proc connection objects
   * that were unassigned (added==true) or were assigned to a now defunct measProc (added==false)
   *
   */
  def onMeasProcAppChanged(context: RequestContext, app: ApplicationInstance, added: Boolean) {
    val rechecks = if (added) {
      measProcTable.where(measProc => measProc.applicationId.isNull).toList
    } else {
      measProcTable.where(measProc => measProc.applicationId === app.id).toList
    }
    logger.info("Meas Proc: " + app.instanceName + " added: " + added + " rechecking: " + rechecks.map { _.endpoint.value.get.entityName })
    rechecks.foreach { checkMeasProcAssignment(context, _) }
  }

  /**
   * looks for the least loaded meas proc instance
   */
  private def checkMeasProcAssignment(context: RequestContext, assign: MeasProcAssignment) {
    val applicationId = getMeasProc().map { _.id }
    logger.info(assign.endpoint.value.get.entityName + " assigned MeasProc: " + applicationId)
    val assignedTime = applicationId.map { x => System.currentTimeMillis }
    val serviceRoutingKey = applicationId.map { x => "meas_batch_" + assign.endpoint.value.get.entityName }
    if (assign.applicationId != applicationId) {
      val newAssign = assign.copy(
        applicationId = applicationId,
        assignedTime = assignedTime,
        serviceRoutingKey = serviceRoutingKey,
        readyTime = None)
      measProcModel.update(context, newAssign, assign)
    }
  }

}