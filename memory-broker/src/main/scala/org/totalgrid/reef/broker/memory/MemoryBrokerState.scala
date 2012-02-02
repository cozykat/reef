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
package org.totalgrid.reef.broker.memory

import collection.immutable.{ Queue => ScalaQueue }
import net.agileautomata.executor4s._
import org.totalgrid.reef.broker._

// classes are 100% immutable and safe to read on multiple threads or to use with STM
object MemoryBrokerState {

  /**
   * side effects to outside systems (callbacks, IO etc) should be done outside of the "critial
   * section" during updates. This should also make the system more usable under STM (if we can
   * reset this object each time STM fails the transaction)
   */
  class SideEffectHolder {

    private val delayedOperations = scala.collection.mutable.Queue.empty[() => Unit]

    def enqueue(fun: => Unit) {
      delayedOperations.enqueue(() => fun)
    }

    def execute() {
      delayedOperations.foreach(_())
    }
  }

  def matches(routingKey: String, bindingKey: String): Boolean = {
    val r = routingKey.split('.')
    val b = bindingKey.split('.')

    // if there isn't a multi section matcher, the keys need to be the same length to match
    if (!bindingKey.contains("#") && r.size != b.size) return false

    //once we find a section with a '#' the rest of the key doesn't matter
    val removed_hashes = b.takeWhile(!_.contains("#"))
    val nh = removed_hashes.zip(r)
    nh.forall(tuple => tuple._2.matches(tuple._1.replaceAll("\\*", ".*")))
  }

  case class State(
      exchanges: Map[String, Exchange] = Map.empty[String, Exchange],
      queues: Map[String, Queue] = Map.empty[String, Queue]) {

    private def withQueue[A](name: String)(fun: Queue => A): A = fun(queues.get(name).getOrElse(throw new Exception("Queue not declared: " + name)))
    private def withExchange[A](name: String)(fun: Exchange => A): A = fun(exchanges.get(name).getOrElse(throw new Exception("Exchange not declared: " + name)))
    private def withQueueAndExchange[A](queue: String, exchange: String)(fun: (Queue, Exchange) => A): A =
      withQueue(queue)(q => withExchange(exchange)(ex => fun(q, ex)))

    def publish(exchange: String, key: String, msg: BrokerMessage, sideEffectHolder: SideEffectHolder): State = withExchange(exchange) { ex =>
      val tuples = ex.getMatches(key).map { name =>
        withQueue(name)(q => (name, q.publish(msg, sideEffectHolder)))
      }
      this.copy(queues = queues ++ tuples)
    }

    def declareQueue(name: String, exe: Executor): State = queues.get(name) match {
      case Some(q) =>
        this
      case None =>
        this.copy(queues = queues + (name -> Queue(name, Strand(exe))))
    }

    def declareExchange(name: String, typ: String): State = {
      exchanges.get(name) match {
        case Some(ex) =>
          if (ex.typ == typ) this
          else throw new Exception("Exchange already declared with type: " + ex.typ)
        case None =>
          this.copy(exchanges = exchanges + (name -> Exchange(name, typ)))
      }
    }

    def bindQueue(queue: String, exchange: String, key: String, unbindFirst: Boolean): State =
      withQueueAndExchange(queue, exchange) { (q, ex) =>
        this.copy(exchanges = exchanges + (exchange -> ex.bindQueue(q.name, key)))
      }

    def unbindQueue(queue: String, exchange: String, key: String): State =
      withQueueAndExchange(queue, exchange)((q, ex) => this.copy(exchanges = exchanges + (exchange -> ex.unbindQueue(q.name, key))))

    def listen(queue: String, mc: BrokerMessageConsumer): State = withQueue(queue) { q =>
      this.copy(queues = queues + (queue -> q.addConsumer(mc)))
    }

    def dropQueue(queue: String): State = {
      val qs = queues.filterKeys(_ != queue)
      val exs = exchanges.mapValues(e => e.dropQueue(queue))
      State(exs, qs)
    }

  }

  case class Exchange(name: String, typ: String, bindings: List[Binding] = Nil) {

    def getMatches(key: String): List[String] = {
      bindings.flatMap(_.getMatch(key))
    }

    def unbindQueue(queue: String, key: String): Exchange =
      this.copy(bindings = bindings.filterNot(b => (b.key == key) && (b.queue == queue)))

    def bindQueue(queue: String, key: String): Exchange = {
      // remove the identical binding if it exists
      val unBound = unbindQueue(queue, key)
      unBound.copy(bindings = Binding(key, queue) :: unBound.bindings)
    }

    def dropQueue(queue: String): Exchange = this.copy(bindings = bindings.filterNot(_.queue == queue))

  }

  case class Binding(key: String, queue: String) {

    def getMatch(msgKey: String): Option[String] = {
      if (matches(msgKey, key)) Some(queue)
      else None
    }
  }

  // each queue needs to use a seperate strand so messages arrive in the order they were sent
  case class Queue(name: String, exe: Strand, unread: ScalaQueue[BrokerMessage] = ScalaQueue.empty[BrokerMessage], consumers: List[BrokerMessageConsumer] = Nil) {

    def publish(msg: BrokerMessage, sideEffectHolder: SideEffectHolder): Queue = consumers match {
      case Nil =>
        this.copy(unread = unread.enqueue(msg))
      case next :: tail =>
        sideEffectHolder.enqueue {
          exe.execute(next.onMessage(msg))
        }
        this.copy(consumers = tail ::: List(next)) //moves x to end of the list
    }

    def addConsumer(mc: BrokerMessageConsumer): Queue = consumers match {
      case Nil =>
        unread.foreach(msg => exe.execute(mc.onMessage(msg)))
        this.copy(unread = ScalaQueue.empty[BrokerMessage], consumers = List(mc))
      case x =>
        this.copy(consumers = mc :: x)
    }

    def removeConsumer(mc: BrokerMessageConsumer): Queue = this.copy(consumers = consumers.filterNot(_.equals(mc)))
  }

}