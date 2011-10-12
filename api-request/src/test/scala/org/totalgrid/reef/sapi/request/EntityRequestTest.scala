/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.sapi.request

import org.totalgrid.reef.japi.request.builders.EntityRequestBuilders
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.JavaConversions._
import org.totalgrid.reef.proto.Model.{ ReefUUID, Entity, Relationship }
import java.util.UUID

@RunWith(classOf[JUnitRunner])
class EntityRequestTest
    extends ClientSessionSuite("Entity.xml", "Entity",
      <div>
        <p>
          An Entity represents a generic component of a system model. Entities are modeled by a name
        and some number of types which describe the entity's role in the system. Entities
        also contain a list of Relationship objects which describe connections/associations
        with other entities in the system.
        </p>
        Entity relationships have a type ("relationship"), a direction ("descendant_of"), and
  a list of Entity objects the relationship connects to. Because relationships are transitive,
  they also have a distance, or how many edges away the other entities are.
        <p>
          Together, entities connected by a certain relationship type form an acyclic directed
        graph. The model must not include cycles (given a start entity, it must not be possible
        to get back to the same entity using the same relationship type and direction).
        </p>
      </div>)
    with ShouldMatchers {

  test("Simple gets") {

    val allEntities = client.get(EntityRequestBuilders.getAll).await().expectMany()
    val targetUuid = allEntities.head.getUuid

    client.addExplanation("Get by UID", "Finds a specific entity by UID.")
    client.get(EntityRequestBuilders.getByUid(targetUuid)).await().expectOne
  }

  test("Get by types") {

    client.addExplanation("Get by type", "Find all entities that match a given type.")
    client.get(EntityRequestBuilders.getByType("Breaker")).await().expectMany()

    client.addExplanation("Get by types", "Find all entities that match at least one of the specified types.")
    client.get(EntityRequestBuilders.getByTypes(List("Agent", "PermissionSet"))).await().expectMany()
  }

  test("Children") {
    val subs = client.get(EntityRequestBuilders.getByType("Substation")).await().expectMany()
    val subUid = subs.head.getUuid

    client.addExplanation("Get descendants", "Finds all descendants of the root entity with the relationship \"owns\".")
    client.get(EntityRequestBuilders.getAllRelatedChildrenFromRootUid(subUid, "owns")).await().expectMany()

    client.addExplanation("Get direct descendants", "Finds all descendants of the root entity with the relationship \"owns\" and that are a distance 1 away.")
    client.get(EntityRequestBuilders.getDirectChildrenFromRootUid(subUid, "owns")).await().expectMany()

    client.addExplanation("Get descendants of a type", "Finds all descendants of the root entity with the relationship \"owns\" and that are of type \"Point\".")
    client.get(EntityRequestBuilders.getOwnedChildrenOfTypeFromRootUid(subUid, "Point")).await().expectMany()

  }

  test("Abstract root") {

    val desc = <div>Instead of starting the query with a UID, this finds all the entities of type "Point" and any "feedback" relationships to entities of type "Command"</div>

    client.addExplanation("Abstract root set", desc)
    client.get(EntityRequestBuilders.getAllPointsAndRelatedFeedbackCommands()).await().expectMany()

  }

  test("Multilevel") {
    val subs = client.get(EntityRequestBuilders.getByType("Substation")).await().expectMany()
    val subUid = subs.head.getUuid

    val desc = <div>Starting from a root node ("Substation"), the request asks for children of type "Breaker", and children of those of type "Point".</div>

    client.addExplanation("Multi-level tree query", desc)
    val resp = client.get(EntityRequestBuilders.getAllPointsSortedByOwningEquipment(subUid)).await().expectMany()

  }

  test("Put with UUID") {

    client.delete(Entity.newBuilder.setName("MagicTestObject").build).await.expectMany()

    val uuid = UUID.randomUUID.toString

    val upload = Entity.newBuilder.setUuid(ReefUUID.newBuilder.setUuid(uuid)).setName("MagicTestObject").addTypes("TestType").build

    val created = client.put(upload).await.expectOne

    created.getUuid.getUuid.toString should equal(uuid)
  }
}