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

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.proto.Model.{ Entity => EntityProto, Relationship }
import org.totalgrid.reef.proto.Model.{ Point => PointProto }
import org.totalgrid.reef.models._
import org.totalgrid.reef.services._

import org.totalgrid.reef.services.ServiceResponseTestingHelpers._
import org.totalgrid.reef.messaging.serviceprovider.SilentEventPublishers

@RunWith(classOf[JUnitRunner])
class ModelBasedTests extends DatabaseUsingTestBase with RunTestsInsideTransaction {

  override def beforeEach() {
    super.beforeEach()
    transaction {
      ModelSeed.seed()
      seedPoints
    }
  }

  def seedPoints {
    EQ.findEntitiesByType(List("Point")).foreach { ent =>
      ApplicationSchema.points.insert(new Point(ent.id, false))
    }
  }

  test("Point model lookup") {
    val pubs = new SilentEventPublishers
    val models = new ModelFactories(pubs, new SilentSummaryPoints)
    val service = new PointService(models.points)

    val entReq =
      EntityProto.newBuilder
        .setName("Pittsboro")
        .addTypes("Substation")
        .addRelations(
          Relationship.newBuilder
            .setRelationship("owns")
            .setDescendantOf(true)
            .addEntities(
              EntityProto.newBuilder
                .addTypes("Bus")
                .addRelations(Relationship.newBuilder
                  .setRelationship("owns")
                  .setDescendantOf(true)
                  .addEntities(
                    EntityProto.newBuilder
                      .addTypes("Point"))))).build

    val req = PointProto.newBuilder.setEntity(entReq).build
    val specIds = Point.findByNames(List("Pittsboro.B12.Kv", "Pittsboro.B24.Kv")).map(_.entityId).toList
    val resp = service.get(req).expectMany()
    val resultIds = resp.map(x => java.util.UUID.fromString(x.getUuid.getUuid))

    specIds.foldLeft(resultIds) { (left, id) =>
      left.contains(id) should equal(true)
      left.filterNot(_ == id)
    } should equal(Nil)
  }

}
