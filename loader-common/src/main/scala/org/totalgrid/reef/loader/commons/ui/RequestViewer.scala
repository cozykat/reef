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
package org.totalgrid.reef.loader.commons.ui

import java.io.PrintStream
import org.totalgrid.reef.client.operations.Response
import org.totalgrid.reef.client.proto.Envelope.{ BatchServiceRequest, Status, Verb }
import org.totalgrid.reef.util.Timing.Stopwatch
import org.totalgrid.reef.client.operations.RequestListener
import org.totalgrid.reef.client.Promise
import org.totalgrid.reef.client.operations.scl.ScalaPromise._
import org.totalgrid.reef.client.exception.ReefServiceException

class RequestViewer(stream: PrintStream, total: Int, width: Int = 50) extends RequestListener {

  // TODO: remove explicit listen call counting when the futures await() call means listen calls have returned
  private var outstandingCalls = 0

  def onFutureCallback() = this.synchronized {
    outstandingCalls -= 1
    if (outstandingCalls == 0) this.notify()
  }

  def onRequest[A](verb: Verb, request: A, promise: Promise[Response[A]]) {
    if (request.asInstanceOf[AnyRef].getClass != classOf[BatchServiceRequest]) {
      this.synchronized { outstandingCalls += 1 }
      promise.listenFor { response =>
        try {
          update(response.await().getStatus, request.asInstanceOf[AnyRef])
        } catch {
          case rse: ReefServiceException =>
            update(rse.getStatus, request.asInstanceOf[AnyRef])
        }
        onFutureCallback()
      }
    }
  }

  start

  class Counter {
    var sum = 1
    def increment() = sum += 1
  }

  var counts = Map.empty[Status, Counter]
  var classCounts = Map.empty[Class[_], Counter]
  var handled: Int = 0
  val stopwatch = new Stopwatch()

  def start = {
    handled = 0

    stream.println("Processing " + total + " objects")

    stream.print("%6d of %6d ".format(handled, total))
    stream.print("|")
    stream.flush()
  }

  private def getStatusChar(status: Status): String = {
    status match {
      case Status.OK => "o"
      case Status.CREATED => "+"
      case Status.UPDATED => "*"
      case Status.NOT_MODIFIED => "."
      case Status.DELETED => "-"
      case _ => "!"
    }
  }

  private def update(status: Status, request: AnyRef) = this.synchronized {

    stream.print(getStatusChar(status))

    handled += 1
    if (handled % width == 0) {
      stream.print("\n%6d of %6d  ".format(handled, total))
    }

    //stream.print(status.toString + "-" + request.getClass.getSimpleName + "\n")
    stream.flush()

    counts.get(status) match {
      case Some(current) => current.increment
      case None => counts += status -> new Counter
    }
    classCounts.get(request.getClass) match {
      case Some(current) => current.increment
      case None => classCounts += request.getClass -> new Counter
    }
  }

  def finish = {
    this.synchronized { while (outstandingCalls > 0) wait() }
    stream.println("|")
    stream.println("Statistics. Finished in: " + stopwatch.elapsed + " milliseconds")
    counts.foreach {
      case (status, count) =>
        stream.println("\t" + status.toString + "(" + getStatusChar(status) + ")" + " : " + count.sum)
    }
    stream.println("Types: ")
    classCounts.foreach { case (classN, count) => stream.println("\t" + classN.getSimpleName + " : " + count.sum) }
    stream.println
  }
}
