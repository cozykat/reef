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
package org.totalgrid.reef.services.framework

import com.google.protobuf.GeneratedMessage
import org.totalgrid.reef.clientapi.proto.Envelope

trait SubscribeFunctions[ServiceType <: GeneratedMessage] {

  def getRoutingKey(req: ServiceType): String

  /**
   * list of keys to bind to the sub handler with, models can override this
   * function to return multiple binding keys.
   */
  def getSubscribeKeys(req: ServiceType): List[String] = getRoutingKey(req) :: Nil

}

trait SubscribeEventCreation[ServiceType <: GeneratedMessage, A]
    extends SubscribeFunctions[ServiceType] {

  /**
   *  gets the notification proto and routing keys for a new/deleted/updated sql record, this can
   * be overriden to allow having a publish routing key that uses information not contained
   * in the proto.
   */
  def getEventProtoAndKey(entry: A): (ServiceType, List[String]) = {
    val proto = convertToProto(entry)
    val key = getRoutingKey(proto)
    (proto, key :: Nil)
  }

  def convertToProto(entry: A): ServiceType
}

/**
 * Component that observes model changes in order to queue service events
 */
trait EventQueueingObserver[ServiceType <: GeneratedMessage, A]
    extends ModelObserver[A] with SubscribeEventCreation[ServiceType, A] {

  protected def publishEvent(context: RequestContext, event: Envelope.Event, resp: ServiceType, key: String): Unit

  protected def onCreated(context: RequestContext, entry: A): Unit = {
    queueEvent(context, Envelope.Event.ADDED, entry, false)
  }
  protected def onUpdated(context: RequestContext, entry: A): Unit = {
    queueEvent(context, Envelope.Event.MODIFIED, entry, false)
  }
  protected def onDeleted(context: RequestContext, entry: A): Unit = {
    queueEvent(context, Envelope.Event.REMOVED, entry, true)
  }

  /**
   * Queue up a "service notification" event to be sent at the end of the model transaction.
   * @param event  envent type add/remove/modify
   * @param entry  the sql object that is
   * @param currentSnapshot
   *      whether to "render" the protos now or at the end of the transaction, if deleting the
   *      sql object will been deleted and lost access to all linked resources at end of
   *      transaction, if adding, child objects are not generally ready till end of transaction
   */
  def queueEvent(context: RequestContext, event: Envelope.Event, entry: A, currentSnapshot: Boolean) = {
    if (currentSnapshot) {
      val (proto, keys) = getEventProtoAndKey(entry)
      context.operationBuffer.queueInTransaction { keys.foreach(queuePublishEvent(context, event, proto, _)) }
    } else
      context.operationBuffer.queueInTransaction {
        val (proto, keys) = getEventProtoAndKey(entry)
        keys.foreach(queuePublishEvent(context, event, proto, _))
      }
  }

  /**
   * once we have "rendered" the event we still need to hold onto it after we close the SQL transaction
   * that way if the reciever of a subscription update immediatley asks for the object he should find it.
   * (It might still be missing if some other process has deleted the object but that is a seperate issue)
   */
  private def queuePublishEvent(context: RequestContext, event: Envelope.Event, resp: ServiceType, key: String): Unit = {
    context.operationBuffer.queuePostTransaction { publishEvent(context, event, resp, key) }
  }

}