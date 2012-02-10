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
package org.totalgrid.reef.httpbridge.servlets.helpers.argumentsources

import scala.collection.JavaConversions._
import javax.servlet.http.HttpServletRequest
import org.totalgrid.reef.httpbridge.servlets.helpers.ArgumentSource
import org.totalgrid.reef.client.service.proto.Model.{ ReefID, ReefUUID }
import org.totalgrid.reef.client.exception.{ InternalServiceException, BadRequestException }

/**
 * implements ArgumentSource by looking in the request parameters for the arguments. Works
 * with both URI encoded values and POSTed application/x-www-form-urlencoded data.
 */
class ParameterArgumentSource(req: HttpServletRequest) extends ArgumentSource {

  private def convertToValuesFromString[A](valuesAsStrings: List[String], klass: Class[A]): List[A] = {
    val result = klass match {
      case StringClass => valuesAsStrings
      case IntClass => valuesAsStrings.map { _.toInt }
      case LongClass => valuesAsStrings.map { _.toLong }
      case BooleanClass => valuesAsStrings.map { _.toBoolean }
      case ReefUuidClass => valuesAsStrings.map { ReefUUID.newBuilder.setValue(_).build }
      case ReefIdClass => valuesAsStrings.map { ReefID.newBuilder.setValue(_).build }
      case MessageClass => throw new BadRequestException("Cannot handle 'object' types using GET interface, use POST instead")
      case _ => throw new InternalServiceException("Unknown argument class: " + klass.getName)
    }
    result.map { klass.cast(_) }
  }

  def findArgument[A](name: String, klass: Class[A]) = {
    val parameterStrings = Option(req.getParameter(name)).toList

    convertToValuesFromString(parameterStrings, klass).headOption
  }

  def findArguments[A](name: String, klass: Class[A]) = {
    val parameterStrings = Option(req.getParameterValues(name)).orElse(
      Option(req.getParameterValues(name + "[]"))).map { _.toList }.getOrElse(List())

    convertToValuesFromString(parameterStrings, klass)
  }
}