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

import org.totalgrid.reef.models.{ ConfigFile, ApplicationSchema, Entity }

import org.totalgrid.reef.services.framework._
import org.totalgrid.reef.proto.Descriptors

import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.services.core.util.UUIDConversions._
import org.totalgrid.reef.clientapi.exceptions.BadRequestException

import SquerylModel._
import scala.collection.JavaConversions._
import org.totalgrid.reef.proto.Model.{ ConfigFile => ConfigProto }
import java.util.UUID

class ConfigFileService(protected val model: ConfigFileServiceModel)
    extends SyncModeledServiceBase[ConfigProto, ConfigFile, ConfigFileServiceModel]
    with DefaultSyncBehaviors {

  override val descriptor = Descriptors.configFile
}

class ConfigFileServiceModel
    extends SquerylServiceModel[ConfigProto, ConfigFile]
    with EventedServiceModel[ConfigProto, ConfigFile]
    with ConfigFileConversion {

  val table = ApplicationSchema.configFiles

  def addOwningEntity(context: RequestContext, protos: List[ConfigProto], entity: Entity): Unit = {
    protos.foreach(configFileProto => {
      logger.debug("addOwningEntity(): configFileProto: " + configFileProto + ", entity: " + entity)

      // add the entity to the config file protos so if we need to create it the ownership is set
      val configFile: ConfigProto = configFileProto.toBuilder.clearEntities.addEntities(EntityQueryManager.entityToProto(entity)).build

      findRecord(context, configFile) match {
        case Some(found) => updateUsingEntities(context, configFile, found, found.owners.value)
        case None => createFromProto(context, configFile)
      }
    })
  }

  override def createFromProto(context: RequestContext, configFileProto: ConfigProto): ConfigFile = {
    if (!configFileProto.hasMimeType || !configFileProto.hasFile) {
      throw new BadRequestException("Cannot add config file without mimeType and file text set")
    }

    if (!configFileProto.hasName && configFileProto.getEntitiesCount != 1) {
      throw new BadRequestException("Cannot add config file without name or related entity set")
    }

    var uuid: Option[UUID] = configFileProto.uuid

    // if name wasn't set use the uuid for this config file
    val name = if (configFileProto.hasName) configFileProto.getName else {
      uuid = Some(UUID.randomUUID)
      uuid.get.toString
    }

    logger.debug("creating config file from proto: " + configFileProto)
    // make the entity entry for the config file
    val entity: Entity = EntityQueryManager.findOrCreateEntity(name, "ConfigurationFile", uuid)

    val sql = create(context, createModelEntry(configFileProto, entity))
    updateUsingEntities(context, configFileProto, sql, Nil) // add entity edges
    sql
  }

  override def updateFromProto(context: RequestContext, configFileProto: ConfigProto, existing: ConfigFile): Tuple2[ConfigFile, Boolean] = {
    val sql = createModelEntry(configFileProto, existing.entity.value)
    updateUsingEntities(context, configFileProto, sql, existing.owners.value) // add entity edges
    update(context, sql, existing)
  }

  override def preDelete(context: RequestContext, sql: ConfigFile) {
    // TODO: figure out how to break config file owner dependency, just allow delete for now
    //if (!sql.owners.value.isEmpty)
    //  throw new BadRequestException("Cannot delete config file that is owned by: " + sql.owners.value.map { _.name })
  }

  override def postDelete(context: RequestContext, sql: ConfigFile) {
    EntityQueryManager.deleteEntity(sql.entity.value)
  }

  private def updateUsingEntities(context: RequestContext, configFileProto: ConfigProto, sql: ConfigFile, existingEntities: List[Entity]) {

    val updatedEntities = configFileProto.getEntitiesList.toList.map { e => EntityQueryManager.findEntity(e).get }
    val newEntitites = updatedEntities.diff(existingEntities)

    // TODO we don't delete edges this way, currently no way to delete configFile edges

    newEntitites.foreach(addUserEntity(context, sql.entity.value, _))
    if (!newEntitites.isEmpty) sql.changedOwners = true
  }

  private def addUserEntity(context: RequestContext, configFile: Entity, user: Entity): Unit = {
    EntityQueryManager.addEdge(user, configFile, "uses")
  }
}

trait ConfigFileConversion extends UniqueAndSearchQueryable[ConfigProto, ConfigFile] {

  def getRoutingKey(configFileProto: ConfigProto) = ProtoRoutingKeys.generateRoutingKey {
    configFileProto.uuid.uuid :: configFileProto.name :: configFileProto.mimeType :: Nil
  }

  def searchQuery(proto: ConfigProto, sql: ConfigFile) = {

    // when searching we go through all the entities in the proto constucting the intersection of the used config files
    val entities = EntityQueryManager.findEntities { proto.getEntitiesList.toList }
    val configEntityIds = entities.map { e => EntityQueryManager.getChildrenOfType(e.id, "uses", "ConfigurationFile").map { _.id } }.flatten
    // if we have specified entities only return matching config files they own (which will be zero if configEntityIds.size == 0)
    val query = if (entities.isEmpty) Nil else Some(sql.entityId in configEntityIds) :: Nil

    List(proto.mimeType.asParam(sql.mimeType === _)) ::: query
  }

  def uniqueQuery(proto: ConfigProto, sql: ConfigFile) = {
    val eSearch = EntitySearch(proto.uuid.uuid, proto.name, proto.name.map(x => List("ConfigurationFile")))
    List(
      eSearch.map(es => sql.entityId in EntityPartsSearches.searchQueryForId(es, { _.id })))
  }

  def isModified(entry: ConfigFile, existing: ConfigFile): Boolean = {
    entry.mimeType.compareTo(existing.mimeType) != 0 || !entry.file.sameElements(existing.file) || entry.changedOwners
  }

  def createModelEntry(proto: ConfigProto, entity: Entity): ConfigFile = {
    val sql = new ConfigFile(
      entity.id,
      proto.getMimeType(),
      proto.getFile.toByteArray)

    sql.entity.value = entity

    sql
  }

  import org.totalgrid.reef.services.framework.ProtoSerializer.convertBytesToByteString
  def convertToProto(entry: ConfigFile): ConfigProto = {
    val configProtoBuilder = ConfigProto.newBuilder
      .setUuid(makeUuid(entry))
      .setName(entry.entity.value.name)
      .setMimeType(entry.mimeType)
      .setFile(entry.file)

    entry.owners.value.foreach(e => configProtoBuilder.addEntities(EntityQueryManager.entityToProto(e)))

    configProtoBuilder.build
  }
}
