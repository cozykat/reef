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
import org.totalgrid.reef.client.service.proto.Auth._
import org.totalgrid.reef.client.service.proto.Events._
import org.totalgrid.reef.client.proto.Envelope
import org.totalgrid.reef.client.proto.Envelope.Status
import org.totalgrid.reef.client.sapi.service.SyncServiceBase
import org.totalgrid.reef.services.core.util._
import org.totalgrid.reef.client.service.proto.Descriptors
import org.totalgrid.reef.client.service.proto.Auth.{ PermissionSet => RoleProto }

import scala.collection.JavaConversions._
import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.client.service.proto.OptionalProtos._
import SquerylModel._
import org.totalgrid.reef.client.exception.BadRequestException
import org.totalgrid.reef.event.{ SystemEventSink, EventType }
import org.totalgrid.reef.models.{
  Agent,
  ApplicationSchema,
  AuthToken => AuthTokenModel,
  AuthTokenPermissionSetJoin
}

// Implicit squeryl list -> query conversion

/**
 * static seed function to bootstrap users + permissions into the system
 * TODO: remove static user seed data
 */
object AuthTokenService {
  import org.totalgrid.reef.models.{ Agent, PermissionSet, AgentPermissionSetJoin }

  def seed(context: RequestContext, systemPassword: String) = {

    val entityModel = new EntityServiceModel
    val agentModel = new AgentServiceModel

    val system = ApplicationSchema.agents.insert(agentModel.createAgentWithPassword(context, "system", systemPassword))

    val allSelector = EntitySelector.newBuilder.setSelector("*").setName("all").build

    val all = Permission.newBuilder.setAllow(true).setVerb("*").setResource("*").setSelector(allSelector).build
    val readOnly = Permission.newBuilder.setAllow(true).setVerb("read").setResource("*").setSelector(allSelector).build

    val selfAgent = EntitySelector.newBuilder.setSelector("$self").setName("self").build
    val updatePassword = Permission.newBuilder.setAllow(true).setVerb("update").setResource("agent_password").setSelector(selfAgent).build

    val allRole = RoleProto.newBuilder.setName("all").addPermissions(all)
    val guestRole = RoleProto.newBuilder.setName("read_only").addPermissions(readOnly).addPermissions(updatePassword)

    val defaultExpirationTime = 18144000000L // one month

    def addPermissionSet(proto: RoleProto.Builder) = {
      proto.setDefaultExpirationTime(defaultExpirationTime)
      val entity = entityModel.findOrCreate(context, proto.getName, "PermissionSet" :: Nil, None)
      val permissionSet = new PermissionSet(entity.id, proto.build.toByteArray)
      ApplicationSchema.permissionSets.insert(permissionSet)
    }

    val allSet = addPermissionSet(allRole)
    val readOnlySet = addPermissionSet(guestRole)

    ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(allSet.id, system.id))

    (allSet, readOnlySet)
  }

}

/**
 * auth token specific code for searching the sql table and converting from
 */
trait AuthTokenConversions extends UniqueAndSearchQueryable[AuthToken, AuthTokenModel] {

  val table = ApplicationSchema.authTokens

  def sortResults(list: List[AuthToken]) = list.sortBy(_.getExpirationTime)

  def getRoutingKey(req: AuthToken) = ProtoRoutingKeys.generateRoutingKey {
    req.loginLocation :: req.agent.name :: Nil
  }

  def relatedEntities(entries: List[AuthTokenModel]) = {
    entries.map { _.agent.value.entity.value }
  }

  def uniqueQuery(proto: AuthToken, sql: AuthTokenModel) = {
    List(
      proto.agent.map(agent => sql.agentId in AgentConversions.uniqueQueryForId(agent, { _.id })),
      proto.loginLocation.asParam(sql.loginLocation === _),
      proto.token.asParam(sql.token === _))
  }

  def searchQuery(proto: AuthToken, sql: AuthTokenModel) = {
    Nil
  }

  def isModified(existing: AuthTokenModel, updated: AuthTokenModel): Boolean = {
    existing.expirationTime != updated.expirationTime
  }

  def convertToProto(entry: AuthTokenModel): AuthToken = {
    val b = AuthToken.newBuilder
    b.setAgent(AgentConversions.convertToProto(entry.agent.value))
    b.setExpirationTime(entry.expirationTime)
    b.setLoginLocation(entry.loginLocation)
    entry.permissionSets.value.foreach(ps => b.addPermissionSets(PermissionSetConversions.convertToProto(ps)))
    b.setToken(entry.token).build
  }

}

class AuthTokenServiceModel
    extends SquerylServiceModel[Long, AuthToken, AuthTokenModel]
    with EventedServiceModel[AuthToken, AuthTokenModel]
    with AuthTokenConversions
    with ServiceModelSystemEventPublisher {

  override def createFromProto(context: RequestContext, authToken: AuthToken): AuthTokenModel =
    {
      logger.info("logging in agent: " + authToken.getAgent.getName)
      val currentTime: Long = System.currentTimeMillis // need one time for authToken DB entry and posted event

      val agentName: String = authToken.agent.name.getOrElse(postLoginException(context, Status.BAD_REQUEST, "Cannot login without setting agent name."))
      // set the user name for systemEvent publishing
      context.modifyHeaders(_.setUserName(agentName))

      // check the password, PUNT: maybe replace this with a nonce + MD5 or something better
      val agentRecord: Option[Agent] = AgentConversions.findRecord(context, authToken.getAgent)
      agentRecord match {
        case None =>
          logger.info("unable to find agent: " + authToken.getAgent)
          postLoginException(context, Status.UNAUTHORIZED, "Invalid agent or password")

        case Some(agent) =>
          if (!agent.checkPassword(authToken.getAgent.getPassword)) {
            logger.debug("invalid password supplied for agent: " + authToken.getAgent.getName)
            postLoginException(context, Status.UNAUTHORIZED, "Invalid agent or password")
          }
          processLogin(context, agent, authToken, currentTime)
      }
    }

  def processLogin(context: RequestContext, agent: Agent, authToken: AuthToken, currentTime: Long): AuthTokenModel =
    {
      if (!agent.checkPassword(authToken.getAgent.getPassword)) {
        postLoginException(context, Status.UNAUTHORIZED, "Invalid agent or password")
      }

      val availableSets = agent.permissionSets.value.toList // permissions we can have
      // permissions we are asking for allow the user to request either all of their permission sets or just a subset, barf if they
      // ask for permisions they dont have
      val permissionsRequested = authToken.getPermissionSetsList.toList
      val setQuerySize = permissionsRequested.map(ps => PermissionSetConversions.searchQuerySize(ps)).sum
      val permissionSets = if (setQuerySize > 0) {
        val askedForSets = permissionsRequested.map(ps => PermissionSetConversions.findRecords(context, ps)).flatten.distinct
        val unavailableSets = askedForSets.diff(availableSets)
        if (unavailableSets.size > 0) {
          postLoginException(context, Status.UNAUTHORIZED, "No access to permission sets: " + unavailableSets)
        }
        askedForSets
      } else {
        availableSets
      }

      // allow the user to set the expiration time explicitly or use the default from the most restrictive permissionset
      val expirationTime = if (authToken.hasExpirationTime) {
        val time = authToken.getExpirationTime
        if (time <= currentTime) {
          postLoginException(context, Status.BAD_REQUEST, "Expiration time cannot be in the past")
        }
        time
      } else {
        // one month
        currentTime + 18144000000L
      }

      // For now, just warn if the client version is unknown
      val version = if (authToken.hasClientVersion) {
        authToken.getClientVersion
      } else {
        logger.warn("Client attempting to login with unknown version")
        "Unknown"
      }

      // TODO: generate an unguessable security token
      val token = java.util.UUID.randomUUID().toString
      val newAuthToken = table.insert(new AuthTokenModel(token, agent.id, authToken.getLoginLocation, version, expirationTime))
      // link the token to all of the permisisonsSet they have checked out access to
      permissionSets.foreach(ps => ApplicationSchema.tokenSetJoins.insert(new AuthTokenPermissionSetJoin(ps.id, newAuthToken.id)))

      postSystemEvent(context, EventType.System.UserLogin, args = List("user" -> agent.entity.value.name))
      newAuthToken
    }

  def postLoginException[A](context: RequestContext, status: Status, reason: String): A = {
    postSystemEvent(context, EventType.System.UserLoginFailure, args = "reason" -> reason :: Nil)
    throw new BadRequestException(reason, status)
  }

  override def updateFromProto(context: RequestContext, req: AuthToken, existing: AuthTokenModel): (AuthTokenModel, Boolean) = {
    throw new Exception("cannot update auth tokens")
  }

  // we are faking the delete operation, we actually want to keep the row around forever as an audit log
  override def delete(context: RequestContext, entry: AuthTokenModel): AuthTokenModel = {

    entry.expirationTime = -1
    table.update(entry)

    context.modifyHeaders(_.setUserName(entry.agent.value.entityName))
    postSystemEvent(context, EventType.System.UserLogout)

    onUpdated(context, entry)
    postDelete(context, entry)
    entry
  }

}

import ServiceBehaviors._

class AuthTokenService(protected val model: AuthTokenServiceModel)
    extends SyncModeledServiceBase[AuthToken, AuthTokenModel, AuthTokenServiceModel]
    with GetEnabled
    with PutOnlyCreates
    with DeleteEnabled {

  override val descriptor = Descriptors.authToken

  override def performCreate(context: RequestContext, model: ServiceModelType, request: ServiceType): ModelType = {
    model.createFromProto(context, request)
  }
}
