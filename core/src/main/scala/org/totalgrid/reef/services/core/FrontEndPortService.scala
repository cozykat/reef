/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.services.core

import org.totalgrid.reef.proto.FEP.{ Port => PortProto }
import org.totalgrid.reef.models.{ ApplicationSchema, FrontEndPort }

import org.totalgrid.reef.services.framework._
import org.totalgrid.reef.services.{ ServiceEventPublishers, ServiceSubscriptionHandler }

import org.totalgrid.reef.proto.Envelope
import org.totalgrid.reef.messaging.ProtoSerializer._
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Table
import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.services.ProtoRoutingKeys
import org.totalgrid.reef.messaging.ProtoServiceException

import OptionalProtos._ // implicit proto properties
import SquerylModel._ // implict asParam
import org.totalgrid.reef.util.Optional._

class FrontEndPortService(protected val modelTrans: ServiceTransactable[FrontEndPortServiceModel])
    extends BasicProtoService[PortProto, FrontEndPort, FrontEndPortServiceModel] {

  def deserialize(bytes: Array[Byte]) = PortProto.parseFrom(bytes)
  val servedProto: Class[_] = classOf[PortProto]
}

class FrontEndPortModelFactory(pub: ServiceEventPublishers)
    extends BasicModelFactory[PortProto, FrontEndPortServiceModel](pub, classOf[PortProto]) {

  def model = new FrontEndPortServiceModel(subHandler)
}

class FrontEndPortServiceModel(protected val subHandler: ServiceSubscriptionHandler)
    extends SquerylServiceModel[PortProto, FrontEndPort]
    with EventedServiceModel[PortProto, FrontEndPort]
    with FrontEndPortConversion {
}

trait FrontEndPortConversion
    extends MessageModelConversion[PortProto, FrontEndPort]
    with UniqueAndSearchQueryable[PortProto, FrontEndPort] {

  val table = ApplicationSchema.frontEndPorts

  def getRoutingKey(req: PortProto) = ProtoRoutingKeys.generateRoutingKey {
    req.name ::
      req.ip.network ::
      req.serial.location :: Nil
  }

  def searchQuery(proto: PortProto, sql: FrontEndPort) = {
    Nil
  }

  def uniqueQuery(proto: PortProto, sql: FrontEndPort) = {
    proto.uid.asParam(sql.id === _.toLong) ::
      proto.name.asParam(sql.name === _) ::
      Nil
  }

  def isModified(entry: FrontEndPort, existing: FrontEndPort): Boolean = {
    true
  }

  def createModelEntry(proto: PortProto): FrontEndPort = {
    new FrontEndPort(
      proto.getName,
      proto.ip.network,
      proto.serial.location,
      proto.toByteString.toByteArray)
  }

  def convertToProto(entry: FrontEndPort): PortProto = {
    PortProto.parseFrom(entry.proto).toBuilder
      .setUid(entry.id.toString)
      .build
  }
}
