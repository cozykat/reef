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
package org.totalgrid.reef.models

import java.util.UUID
import org.totalgrid.reef.models.UUIDConversions._
import org.totalgrid.reef.client.service.proto.Model.{ Relationship, Entity => EntityProto }
import org.totalgrid.reef.client.service.proto.OptionalProtos._
import org.squeryl.{ Queryable, Query }
import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.models.ApplicationSchema._
import org.totalgrid.reef.models.SquerylConversions._
import org.squeryl.dsl.ast.{ ExpressionNode, LogicalBoolean }
import org.totalgrid.reef.client.exception.BadRequestException

import org.totalgrid.reef.client.sapi.types.Optional._
import scala.collection.JavaConversions._

object EntityTreeQuery {
  /**
   * Entity-to-entity relationship.
   */
  case class Relate(rel: String, descendantOf: Boolean, dist: Int)

  /**
   * Tree node of entity query results, wraps an Entity (this node) with its
   * relationships to subnodes.
   */
  case class ResultNode(val ent: Entity, val subNodes: Map[Relate, List[ResultNode]]) {
    def id = ent.id
    def name = ent.name

    def types = {
      if (_types == None) {
        _types = Some(ent.types.value.toList)
      }
      _types.get
    }
    def types_=(l: List[String]) = { _types = Some(l) }
    protected var _types: Option[List[String]] = None

    def traverse[A](getEntry: ResultNode => A, f: ResultNode => Boolean): List[A] = {
      val subIds = for ((rel, nodes) <- subNodes; node <- nodes) yield node.traverse(getEntry, f)
      val list = subIds.toList.flatten

      if (f(this))
        getEntry(this) :: list
      else
        list
    }

    /**
     * Traverses tree for a flat list of all ids of entities of a certain type.
     */
    def idsForType(typ: String): List[UUID] = {
      traverse(_.id, _.types.contains(typ))
    }

    /**
     * Traverses tree for a flat list of all ids of returned entities
     */
    def flatIds(): List[UUID] = {
      traverse(_.id, x => true)
    }

    def flatEntites(): List[Entity] = {
      traverse(_.ent, x => true)
    }

    /**
     * Traverses tree to create Entity/Relationship proto tree.
     */
    def toProto: EntityProto = {
      val b = EntityProto.newBuilder.setUuid(makeUuid(ent)).setName(ent.name)
      types.sorted.foreach(b.addTypes(_))
      for ((rel, nodes) <- subNodes) {
        val r = Relationship.newBuilder
          .setRelationship(rel.rel)
          .setDescendantOf(rel.descendantOf)
          .setDistance(rel.dist)

        nodes.foreach(n => r.addEntities(n.toProto))
        b.addRelations(r)
      }
      b.build
    }

    /**
     * Visualizes the result tree.
     */
    def prettyPrint(indent: String): String = {
      val in = indent + "  "
      var str = "\n" + indent + "Entity { \n"
      str += in + "id: " + ent.id + "\n"
      str += in + "name: " + ent.name + "\n"
      str += in + ent.types.value.map("type: " + _).mkString("\n" + in)
      for ((rel, nodes) <- subNodes) {
        val in2 = in + "  "
        str += "\n"
        str += in + "Rel { \n"
        str += in2 + "rel: " + rel.rel + "\n"
        str += in2 + "descOf: " + rel.descendantOf + "\n"
        str += in2 + "dist: " + rel.dist + "\n"
        str += nodes.map(_.prettyPrint(in2)).mkString("")
        str += "\n" + in + "} \n"
      }

      str += "\n" + indent + "} \n"
      str
    }
    override def toString = {
      prettyPrint("")
    }
  }

  /**
   * Builder for assembling a result tree from query node tree.
   */
  class ResultNodeBuilder(val ent: Entity) {
    protected var subs = Map[Relate, List[ResultNodeBuilder]]()
    def addSubNode(rel: Relate, node: ResultNodeBuilder) = {
      subs += (rel -> (node :: (subs.get(rel) getOrElse Nil)))
    }

    def id = ent.id
    def build: ResultNode = ResultNode(ent, subs.mapValues(_.map(_.build)))
  }

  /**
   * Represents a subtree of the specifications of sets of subnodes. Specifications
   * consist of relationship information and partial descriptions of sub-entities.
   */
  case class QueryNode(
      val rel: Option[String],
      val descendantOf: Option[Boolean],
      val dist: Option[Int],
      val name: Option[String],
      val types: List[String],
      val subQueries: List[QueryNode],
      selector: ExpressionNode => LogicalBoolean) {

    /**
     * Given an upper set of entities, find the lower set specified by this query node,
     * and recurse.
     *
     * @param upperQuery Squeryl query that represents upper set
     * @param upperNodes Set of mutable result tree-nodes to be filled out
     */
    def fillChildren(upperQuery: Query[Entity], upperNodes: List[ResultNodeBuilder]) {

      // short circuit the queries if we have no parent nodes
      if (upperNodes.isEmpty) return

      val upperIds = upperNodes.map(_.id)
      val upperIdMap = upperNodes.map(n => (n.id, n)).toMap

      //if (ids != ids.distinct) throw new Exception("Tree is not unique, same node has multiple links to itself, check model.")

      val entEdges = lowerQuery(upperIds)
      val entsOnlyQuery = from(entEdges)(entEdge => select(entEdge._1))

      val nodes = entEdges.map {
        case (ent, edge) =>
          val rel = Relate(edge.relationship, edge.childId == ent.id, edge.distance)
          val upperId = if (edge.childId == ent.id) edge.parentId else edge.childId
          val node = new ResultNodeBuilder(ent)
          upperIdMap(upperId).addSubNode(rel, node)
          node
      }.toList

      subQueries.foreach(sub => sub.fillChildren(entsOnlyQuery, nodes))
    }

    protected def lowerQuery(upperIds: List[UUID]) = {
      from(entities, edges)((lowEnt, edge) =>
        where(expressionForThisNode(lowEnt, edge, upperIds))
          select ((lowEnt, edge)))
    }

    protected def expressionForThisNode(ent: Entity, edge: EntityEdge, upperIds: List[UUID]): LogicalBoolean = {

      val optList: List[Option[LogicalBoolean]] = List(name.map(ent.name === _),
        (types.size > 0) thenGet (ent.id in EntityQuery.entityIdsFromTypes(types)),
        rel.map(edge.relationship === _),
        dist.map(edge.distance === _),
        Some(selector(ent.id)))

      def childQ = (ent.id === edge.childId) and (edge.parentId in upperIds)
      def parentQ = (ent.id === edge.parentId) and (edge.childId in upperIds)

      val foreignKey = descendantOf match {
        case Some(true) => childQ
        case Some(false) => parentQ
        case None => childQ or parentQ
      }

      foreignKey :: optList.flatten
    }
  }

  implicit def queryNodeToList(node: QueryNode): List[QueryNode] = List(node)

  /**
   * Executes a recursive search to go from a set of query trees to a set of result trees.
   *
   * @param queries Query tree root nodes
   * @param rootSelect Squeryl/sql select that represents the root set
   * @return Result tree root nodes (maps to rootSet) filled out by query
   */
  def resultsForQuery(queries: List[QueryNode], rootSelect: Query[Entity]): List[ResultNode] = {
    resultsForQuery(queries, rootSelect.toList, rootSelect)
  }

  /**
   * Executes a recursive search to go from a set of query trees to a set of result trees.
   *
   * @param queries Query tree root nodes
   * @param rootSet Entities from root query
   * @param rootSelect Squeryl/sql select that represents the root set
   * @return Result tree root nodes (maps to rootSet) filled out by query
   */
  def resultsForQuery(queries: List[QueryNode], rootSet: List[Entity], rootSelect: Query[Entity]): List[ResultNode] = {
    val results = rootSet.map(new ResultNodeBuilder(_)).toList
    queries.foreach(_.fillChildren(rootSelect, results))
    results.map(_.build)
  }

  /**
   * Translates a proto entity tree query to the internal, useful
   * representation.
   *
   * @param proto Proto representation of a entity tree query
   * @return List of tree subqueries.
   */
  def protoToQuery(proto: EntityProto, selector: ExpressionNode => LogicalBoolean): List[QueryNode] = {
    def buildSubQuery(rel: Relationship): List[QueryNode] = {
      if (rel.getEntitiesCount > 0) {
        rel.getEntitiesList.toList.map { ent =>
          val subs = ent.getRelationsList.flatMap(buildSubQuery(_)).toList
          QueryNode(rel.relationship, rel.descendantOf, rel.distance, ent.name, ent.getTypesList.toList, subs, selector)
        }
      } else {
        QueryNode(rel.relationship, rel.descendantOf, rel.distance, None, Nil, Nil, selector)
      }
    }

    proto.getRelationsList.flatMap(buildSubQuery(_)).toList
  }

  /**
   * Interprets a proto object as a entity tree query, gets the root set of
   * entities, and retrieves the query results.
   *
   * @param proto Proto representation of a entity tree query
   * @return List of root nodes representing result trees.
   */
  def protoTreeQuery(proto: EntityProto, selector: ExpressionNode => LogicalBoolean): List[ResultNode] = {

    // For the moment not allowing a root set of everything
    if (proto.uuid.value == None && proto.name == None && proto.getTypesCount == 0)
      throw new BadRequestException("Must specify root set")

    def expr(ent: Entity, typ: EntityToTypeJoins) = {
      Some(selector(ent.id)) ::
        proto.uuid.value.map(ent.id === UUID.fromString(_)) ::
        proto.name.map(ent.name === _) ::
        ((proto.getTypesCount > 0) thenGet ((typ.entType in proto.getTypesList.toList)
          and (typ.entityId === ent.id))) ::
          Nil
    }

    // If query specifies type, do a join, otherwise simpler query on id/name
    val rootQuery = if (proto.getTypesCount != 0) {
      from(entities, entityTypes)((ent, typ) =>
        where((expr(ent, typ).flatten))
          select (ent)).distinct
    } else {
      from(entities)(ent =>
        where(((proto.uuid.value.map(ent.id === UUID.fromString(_)) ::
          proto.name.map(ent.name === _) :: Nil).flatten))
          select (ent))
    }

    // Execute query (unless root set is nil)
    if (rootQuery.size == 0) Nil
    else resultsForQuery(protoToQuery(proto, selector), rootQuery)
  }

  private val allEntitySelector = { uuid: ExpressionNode => true === true }

  def protoTreeQuery(proto: EntityProto): List[ResultNode] = {
    protoTreeQuery(proto, allEntitySelector)
  }

  /**
   * Return all ids of a certain entity type retrieved by an entity query.
   *
   * @param proto Proto representation of a entity tree query
   * @param typ Entity type to return ids of
   * @return List of entity ids from entity query of specified type
   */
  def typeIdsFromProtoQuery(proto: EntityProto, typ: String): List[UUID] = {
    protoTreeQuery(proto, allEntitySelector).flatMap(_.idsForType(typ))
  }

  /**
   * Return a list of descendants of the entity aUUID with this entity.
   */
  def idsFromProtoQuery(proto: EntityProto): List[UUID] = {
    protoTreeQuery(proto, allEntitySelector).flatMap(_.flatIds())
  }
}
