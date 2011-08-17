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
import org.totalgrid.reef.proto.Auth._
import org.totalgrid.reef.proto.Events._
import org.totalgrid.reef.japi.Envelope.Status
import org.totalgrid.reef.sapi.service.SyncServiceBase
import org.totalgrid.reef.services.core.util._
import org.totalgrid.reef.models.{ ApplicationSchema, AuthToken => AuthTokenModel, AuthTokenPermissionSetJoin, Agent => AgentModel, PermissionSet => PermissionSetModel, AuthPermission, EventStore }
import org.totalgrid.reef.proto.Descriptors

import scala.collection.JavaConversions._
import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.proto.OptionalProtos._
import SquerylModel._
import org.totalgrid.reef.messaging.serviceprovider.{ ServiceEventPublishers, ServiceSubscriptionHandler }
import org.totalgrid.reef.japi.{ BadRequestException, Envelope }
import org.totalgrid.reef.services.{ ServiceDependencies, ProtoRoutingKeys }
import org.totalgrid.reef.event.{ SystemEventSink, EventType }

// Implicit squeryl list -> query conversion

/**
 * static seed function to bootstrap users + permissions into the system
 * TODO: remove static user seed data
 */
object AuthTokenService {
  import org.totalgrid.reef.models.{ Agent, AuthPermission, PermissionSet, PermissionSetJoin, AgentPermissionSetJoin }
  def seed() {

    inTransaction {
      if (ApplicationSchema.agents.Count.head == 0) {

        val system = ApplicationSchema.agents.insert(Agent.createAgentWithPassword("system", "system"))

        val core = ApplicationSchema.agents.insert(Agent.createAgentWithPassword("core", "core"))
        val op = ApplicationSchema.agents.insert(Agent.createAgentWithPassword("operator", "operator"))
        val guest = ApplicationSchema.agents.insert(Agent.createAgentWithPassword("guest", "guest"))

        val get_only = ApplicationSchema.permissions.insert(new AuthPermission(true, "*", "get"))
        val read_only = ApplicationSchema.permissions.insert(new AuthPermission(true, "*", "read"))
        val update_password = ApplicationSchema.permissions.insert(new AuthPermission(true, "agent", "update"))

        val all = ApplicationSchema.permissions.insert(new AuthPermission(true, "*", "*"))

        val timeout = 18144000000L // one month

        val read_set = ApplicationSchema.permissionSets.insert(PermissionSet.newInstance("read_only", timeout))
        ApplicationSchema.permissionSetJoins.insert(new PermissionSetJoin(read_set.id, read_only.id))
        ApplicationSchema.permissionSetJoins.insert(new PermissionSetJoin(read_set.id, get_only.id))
        ApplicationSchema.permissionSetJoins.insert(new PermissionSetJoin(read_set.id, update_password.id))

        val all_set = ApplicationSchema.permissionSets.insert(PermissionSet.newInstance("all", timeout))
        ApplicationSchema.permissionSetJoins.insert(new PermissionSetJoin(all_set.id, all.id))

        ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(all_set.id, core.id))
        ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(read_set.id, core.id))
        ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(all_set.id, op.id))
        ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(read_set.id, guest.id))
        ApplicationSchema.agentSetJoins.insert(new AgentPermissionSetJoin(all_set.id, system.id))

      }
    }
  }
}

/**
 * auth token specicic code for searching the sql table and converting from
 */
trait AuthTokenConversions
    extends UniqueAndSearchQueryable[AuthToken, AuthTokenModel] {

  val table = ApplicationSchema.authTokens

  def getRoutingKey(req: AuthToken) = ProtoRoutingKeys.generateRoutingKey {
    req.loginLocation :: req.agent.name :: Nil
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
    extends SquerylServiceModel[AuthToken, AuthTokenModel]
    with EventedServiceModel[AuthToken, AuthTokenModel]
    with AuthTokenConversions
    with ServiceModelSystemEventPublisher {

  override def createFromProto(context: RequestContext, req: AuthToken): AuthTokenModel = {

    val currentTime = System.currentTimeMillis // need one time for authToken DB entry and posted event.

    val agentName: String = req.agent.name.getOrElse(postLoginException(context, Status.BAD_REQUEST, "Cannot login without setting agent name."))
    // set the user name for systemEvent publishing
    context.headers.setUserName(agentName)

    // check the password, PUNT: maybe replace this with a nonce + MD5 or something better
    val agent: AgentModel = AgentConversions.findRecord(context, req.getAgent).getOrElse(postLoginException(context, Status.UNAUTHORIZED, "Invalid agent or password")) // no agent field or unknown agent!
    if (!agent.checkPassword(req.getAgent.getPassword)) {
      postLoginException(context, Status.UNAUTHORIZED, "Invalid agent or password")
    }

    val availableSets = agent.permissionSets.value.toList // permissions we can have
    val permissionsRequested = req.getPermissionSetsList.toList // permissions we are asking for

    // allow the user to request either all of their permission sets or just a subset, barf if they ask for permisions they dont have
    val setQuerySize = permissionsRequested.map(ps => PermissionSetConversions.searchQuerySize(ps)).sum
    val permissionSets = if (setQuerySize > 0) {
      val askedForSets = permissionsRequested.map(ps => PermissionSetConversions.findRecords(context, ps)).flatten.distinct
      val unavailableSets = askedForSets.diff(availableSets)
      if (unavailableSets.size > 0) postLoginException(context, Status.UNAUTHORIZED, "No access to permission sets: " + unavailableSets)
      askedForSets
    } else {
      availableSets
    }

    // allow the user to set the expiration time explicitly or use the default from the
    // most restrictive permissionset 
    val expirationTime = if (req.hasExpirationTime) {
      val time = req.getExpirationTime
      if (time <= currentTime) postLoginException(context, Status.BAD_REQUEST, "Expiration time cannot be in the past")
      time
    } else {
      currentTime + permissionSets.map(ps => ps.defaultExpirationTime).min
    }

    // TODO: generate an unguessable security token
    val token = java.util.UUID.randomUUID().toString

    val authToken = table.insert(new AuthTokenModel(token, agent.id, req.getLoginLocation, expirationTime))

    // link the token to all of the permisisonsSet they have checked out access to
    permissionSets.foreach(ps => ApplicationSchema.tokenSetJoins.insert(new AuthTokenPermissionSetJoin(ps.id, authToken.id)))

    postSystemEvent(context, EventType.System.UserLogin)

    authToken
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

    context.headers.setUserName(entry.agent.value.entityName)
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
}
