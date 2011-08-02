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

import org.totalgrid.reef.sapi.client.SessionPool

import org.totalgrid.reef.proto.Commands
import org.totalgrid.reef.proto.FEP.CommEndpointConnection
import Commands.UserCommandRequest

import org.totalgrid.reef.proto.Descriptors
import org.totalgrid.reef.sapi.service.ServiceTypeIs
import org.totalgrid.reef.sapi.client.Response
import org.totalgrid.reef.japi.{ BadRequestException, Envelope }
import org.totalgrid.reef.sapi.{ RequestEnv, AddressableDestination }

import org.totalgrid.reef.services.framework._
import org.squeryl.PrimitiveTypeMode._
import ServiceBehaviors._
import org.totalgrid.reef.models.{ Command, UserCommandModel }

class UserCommandRequestService(
  protected val modelTrans: ServiceTransactable[UserCommandRequestServiceModel], pool: SessionPool)
    extends AsyncModeledServiceBase[UserCommandRequest, UserCommandModel, UserCommandRequestServiceModel]
    with UserCommandRequestValidation
    with AsyncGetEnabled
    with AsyncPutCreatesOrUpdates
    with SubscribeEnabled {

  override val descriptor = Descriptors.userCommandRequest

  override def doAsyncPutPost(context: RequestContext[_], rsp: Response[UserCommandRequest], callback: Response[UserCommandRequest] => Unit) = {

    val request = rsp.expectOne

    val command = Command.findByNames(request.getCommandRequest.getName :: Nil).single

    val address = command.endpoint.value match {
      case Some(ep) =>
        val frontEndAssignment = ep.frontEndAssignment.value

        val endpointState = CommEndpointConnection.State.valueOf(frontEndAssignment.state)

        if (endpointState != CommEndpointConnection.State.COMMS_UP) {
          throw new BadRequestException("Endpoint: " + ep.entityName + " is not COMMS_UP: " + endpointState)
        }

        frontEndAssignment.serviceRoutingKey match {
          case Some(key) => AddressableDestination(key)
          case None => throw new BadRequestException("No routing info for endpoint: " + ep.entityName)
        }
      case None => throw new BadRequestException("Command has no endpoint set " + request)
    }

    pool.borrow { session =>
      session.put(request, destination = address).listen { response =>
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

  override protected def preCreate(context: RequestContext[_], proto: UserCommandRequest, headers: RequestEnv) = {

    if (!proto.getCommandRequest.hasName)
      throw new BadRequestException("Request must specify command name", Envelope.Status.BAD_REQUEST)

    if (proto.hasStatus)
      throw new BadRequestException("Update must not specify status", Envelope.Status.BAD_REQUEST)

    super.preCreate(context, this.doCommonValidation(proto), headers)
  }

  override protected def preUpdate(context: RequestContext[_], proto: UserCommandRequest, existing: UserCommandModel, headers: RequestEnv) = {

    if (!proto.hasStatus)
      throw new BadRequestException("Update must specify status", Envelope.Status.BAD_REQUEST)

    super.preUpdate(context, doCommonValidation(proto), existing, headers)
  }
}

object UserCommandRequestService {
  val defaultTimeout = 30000
}
