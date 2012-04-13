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
import org.totalgrid.reef.client.service.proto.Commands.{ CommandLock => AccessProto }
import org.totalgrid.reef.models.{ CommandLockModel => AccessModel }
import org.squeryl.PrimitiveTypeMode._
import scala.collection.JavaConversions._

import org.totalgrid.reef.client.service.proto.Descriptors

import ServiceBehaviors._
import org.totalgrid.reef.client.proto.Envelope
import org.totalgrid.reef.client.exception.BadRequestException

import org.totalgrid.reef.client.sapi.client.BasicRequestHeaders
import org.totalgrid.reef.client.service.proto.Descriptors

// checktimeouts is disabled during testing only
class CommandLockService(protected val model: CommandLockServiceModel, checkTimeouts: Boolean = true)
    extends SyncModeledServiceBase[AccessProto, AccessModel, CommandLockServiceModel]
    with GetEnabled
    with SubscribeEnabled
    with PutOnlyCreates
    with DeleteEnabled {

  import AccessProto._

  val defaultSelectTime = 30000

  override val descriptor = Descriptors.commandLock

  def deserialize(bytes: Array[Byte]) = AccessProto.parseFrom(bytes)

  override protected def preCreate(context: RequestContext, proto: AccessProto): AccessProto = {
    // Simple proto validity check
    if (proto.getCommandsList.length == 0)
      throw new BadRequestException("Must specify at least one command")
    if (!proto.hasAccess)
      throw new BadRequestException("Must specify access mode, ALLOWED or BLOCKED")

    // Being a select (allowed) implies you have user and expiry
    if (proto.getAccess == AccessMode.ALLOWED) {

      // Set expire time to default or else use proto as-is
      if (!proto.hasExpireTime) AccessProto.newBuilder(proto).setExpireTime(defaultSelectTime).build
      else {
        if (checkTimeouts && proto.getExpireTime != -1 && proto.getExpireTime <= 0) throw new BadRequestException("Must specify positive timeout or -1 for no timeout.")
        proto
      }
    } else proto
  }

  // overriden to avoid auth checks
  final override protected def performDelete(context: RequestContext, model: ServiceModelType, req: ServiceType): List[AccessModel] = {
    val existing = model.findRecords(context, req)
    existing.foreach(model.removeAccess(context, _))
    existing
  }

}
