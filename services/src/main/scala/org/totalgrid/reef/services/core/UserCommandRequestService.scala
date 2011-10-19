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

import org.totalgrid.reef.api.proto.FEP.CommEndpointConnection
import org.totalgrid.reef.api.sapi.impl.Descriptors
import org.totalgrid.reef.api.sapi.service.ServiceTypeIs
import org.totalgrid.reef.api.japi.{ BadRequestException, Envelope }
import org.totalgrid.reef.services.framework._
import ServiceBehaviors._
import org.totalgrid.reef.models.{ Command, UserCommandModel }
import org.totalgrid.reef.api.proto.Commands.{ CommandStatus, UserCommandRequest }
import com.weiglewilczek.slf4s.Logging
import org.totalgrid.reef.api.sapi.client.{ BasicRequestHeaders, Response }
import org.totalgrid.reef.api.sapi.{ AddressableDestination }

class UserCommandRequestService(
  protected val model: UserCommandRequestServiceModel)
    extends AsyncModeledServiceBase[UserCommandRequest, UserCommandModel, UserCommandRequestServiceModel]
    with UserCommandRequestValidation
    with AsyncGetEnabled
    with AsyncPutCreatesOrUpdates
    with SubscribeEnabled
    with Logging {

  override val descriptor = Descriptors.userCommandRequest

  override def doAsyncPutPost(contextSource: RequestContextSource, rsp: Response[UserCommandRequest], callback: Response[UserCommandRequest] => Unit) = {
    val request = rsp.expectOne

    contextSource.transaction { context =>

      val command = Command.findByNames(request.getCommandRequest.getName :: Nil).single

      val address = command.endpoint.value match {
        case Some(ep) =>
          val frontEndAssignment = ep.frontEndAssignment.value

          val endpointState = CommEndpointConnection.State.valueOf(frontEndAssignment.state)

          if (endpointState != CommEndpointConnection.State.COMMS_UP) {
            throw new BadRequestException("Endpoint: " + ep.entityName + " is not COMMS_UP, current state: " + endpointState)
          }

          frontEndAssignment.serviceRoutingKey match {
            case Some(key) => AddressableDestination(key)
            case None => throw new BadRequestException("No routing info for endpoint: " + ep.entityName)
          }
        case None => throw new BadRequestException("Command has no endpoint set: " + request)
      }
      context.client.put(request, BasicRequestHeaders.empty.setDestination(address)).listen { response =>
        contextSource.transaction { context =>
          model.findRecord(context, request) match {
            case Some(record) =>
              val updatedStatus = if (response.success) {
                response.list.head.getStatus
              } else {
                logger.warn { "Got non successful response to command request: " + request + " dest: " + address + " response: " + response }
                CommandStatus.UNDEFINED
              }
              model.update(context, record.copy(status = updatedStatus.getNumber), record)
            case None =>
              logger.warn { "Couldn't find command request record to update" }
          }
        }
        callback(response)
      }
    }
  }

}

trait UserCommandRequestValidation extends HasCreate with HasUpdate {

  self: ServiceTypeIs[UserCommandRequest] with ModelTypeIs[UserCommandModel] =>

  private def doCommonValidation(proto: UserCommandRequest) = {

    if (!proto.hasCommandRequest)
      throw new BadRequestException("Request must specify command information", Envelope.Status.BAD_REQUEST)

    proto
  }

  override protected def preCreate(context: RequestContext, proto: UserCommandRequest) = {

    if (!proto.getCommandRequest.hasName)
      throw new BadRequestException("Request must specify command name", Envelope.Status.BAD_REQUEST)

    if (proto.hasStatus)
      throw new BadRequestException("Create must not specify status", Envelope.Status.BAD_REQUEST)

    super.preCreate(context, this.doCommonValidation(proto))
  }

  override protected def preUpdate(context: RequestContext, proto: UserCommandRequest, existing: UserCommandModel) = {

    if (!proto.hasStatus)
      throw new BadRequestException("Update must specify status", Envelope.Status.BAD_REQUEST)

    super.preUpdate(context, doCommonValidation(proto), existing)
  }
}

object UserCommandRequestService {
  val defaultTimeout = 30000
}
