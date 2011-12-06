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
package org.totalgrid.reef.loader.commons

import org.totalgrid.reef.client.service.proto.Model._
import org.totalgrid.reef.client.service.proto.FEP._
import org.totalgrid.reef.client.sapi.client.rest.BatchOperations
import com.google.protobuf.GeneratedMessage

class EquipmentRemoverCache extends ModelDeleterCache

/**
 * when we are caching entries for deletion we will delete them by type, not
 * order in the tree so we store each type in its own list
 */
trait ModelDeleterCache extends ModelCollector {

  var points = List.empty[Point]
  var commands = List.empty[Command]
  var endpoints = List.empty[Endpoint]
  var channel = List.empty[CommChannel]
  var equipment = List.empty[Entity]
  var configFiles = List.empty[ConfigFile]

  def addPoint(obj: Point, entity: Entity) = {
    // need to clear off the logicalNode because delete uses searchQuery
    // TODO: fix services so they only first do unique query then search query on delete
    points ::= obj.toBuilder.clearEndpoint.build
  }
  def addCommand(obj: Command, entity: Entity) = {
    commands ::= obj.toBuilder.clearEndpoint.build
  }
  def addEndpoint(obj: Endpoint, entity: Entity) = {
    endpoints ::= obj
  }
  def addChannel(obj: CommChannel, entity: Entity) = {
    channel ::= obj
  }
  def addEquipment(entity: Entity) = {
    equipment ::= entity
  }
  def addConfigFile(obj: ConfigFile, entity: Entity) = {
    configFiles ::= obj
  }
  def addEdge(edge: EntityEdge) = {}

  def doDeletes(local: LoaderServices, batchSize: Int) {
    // we need to delete endpoints first because we can't delete points and commands that
    // are sourced by endpoints
    // NOTE: we need the List.empty[GeneratedMessage] to tell the compiler what the type is, when it tries to guess it can run forever
    val toDelete: List[GeneratedMessage] = endpoints ::: channel ::: commands ::: points ::: equipment ::: configFiles ::: List.empty[GeneratedMessage]
    val toDeleteOps = toDelete.map { entry => (c: LoaderServices) => c.delete(entry) }

    BatchOperations.batchOperations(local, toDeleteOps, batchSize)
  }

  def size = endpoints.size + channel.size + commands.size + points.size + equipment.size + configFiles.size
}