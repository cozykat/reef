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
package org.totalgrid.reef.loader

import scala.collection.mutable.HashMap

/**
 * When loading a configuration, we need to validate the equipment model
 * against the communications model. This class records the points and
 * controls found, then does a validation at the end.
 */
class LoadCache {

  val controls = HashMap[String, CachedControl]()
  val points = HashMap[String, CachedPoint]()

  val loadCacheEqu = new LoadCacheEqu(this)
  val loadCacheCom = new LoadCacheCom(this)

  def validate: Boolean = {
    import ValidateResult._

    var valid = true

    def info(msg: String) = println(msg)
    def warn(msg: String) = {
      valid = false
      println(msg)
    }
    def warnIf(msg: String, b: Boolean) = {
      if (b) warn(msg) else info(msg)
    }
    def spacer() = println

    if (controls.isEmpty && points.isEmpty)
      warn("WARNING: No controls or points found.")

    var pEquOnly = List[CachedObject]()
    var pComOnly = List[CachedObject]()
    var pComplete = List[CachedObject]()
    var pMultiple = List[CachedObject]()
    var pNone = List[CachedObject]()

    if (!points.isEmpty) {

      points.values.foreach(point => {
        point.validate match {
          case NONE => pEquOnly ::= point
          case COMPLETE => pComplete ::= point
          case EQU_ONLY => pEquOnly ::= point
          case COM_ONLY => pComOnly ::= point
          case MULTIPLE => pMultiple ::= point
        }
      })
      pEquOnly = pEquOnly.sortBy(_.name)
      pComOnly = pComOnly.sortBy(_.name)
      pMultiple = pMultiple.sortBy(_.name)
      pNone = pNone.sortBy(_.name)

      info("POINTS SUMMARY:  ")
      info("   Points found in equipmentModel and communicationsModel: " + pComplete.length)
      warnIf("   Points found in equipmentModel only:      " + pEquOnly.length, pEquOnly.nonEmpty)
      warnIf("   Points found in communicationsModel only: " + pComOnly.length, pComOnly.nonEmpty)
      if (pMultiple.length > 0)
        warn("   Points found in both models more than once: " + pMultiple.length)
      if (pNone.length > 0)
        warn("   Points found in neither model (internal error): " + pNone.length)
    }

    var cEquOnly = List[CachedObject]()
    var cComOnly = List[CachedObject]()
    var cComplete = List[CachedObject]()
    var cMultiple = List[CachedObject]()
    var cNone = List[CachedObject]()

    if (!controls.isEmpty) {

      controls.values.foreach(control => {
        control.validate match {
          case NONE => cEquOnly ::= control
          case COMPLETE => cComplete ::= control
          case EQU_ONLY => cEquOnly ::= control
          case COM_ONLY => cComOnly ::= control
          case MULTIPLE => cMultiple ::= control
        }
      })
      cEquOnly = cEquOnly.sortBy(_.name)
      cComOnly = cComOnly.sortBy(_.name)
      cMultiple = cMultiple.sortBy(_.name)
      cNone = cNone.sortBy(_.name)

      info("CONTROLS SUMMARY:  ")
      info("   Controls found in equipmentModel and communicationsModel: " + cComplete.length)
      warnIf("   Controls found in equipmentModel only:      " + cEquOnly.length, cEquOnly.nonEmpty)
      warnIf("   Controls found in communicationsModel only: " + cComOnly.length, cComOnly.nonEmpty)
      if (cMultiple.length > 0)
        warn("   Controls found in one model more than once: " + cMultiple.length)
      if (cNone.length > 0)
        warn("   Controls found in neither model (internal error): " + cNone.length)

    }

    if (pEquOnly.length > 0 || pComOnly.length > 0 || pMultiple.length > 0 || pNone.length > 0) {
      spacer
      warn("POINT WARNINGS:")
      if (pEquOnly.length > 0)
        warn("  Points found in equipmentModel only: \n" + pEquOnly.mkString("    ", "\n    ", "\n"))
      if (pComOnly.length > 0)
        warn("  Points found in communicationsModel only:\n" + pComOnly.mkString("    ", "\n    ", "\n"))
      if (pMultiple.length > 0)
        warn("  Points found in both models more than once:\n" + pMultiple.mkString("    ", "\n    ", "\n"))
      if (pNone.length > 0)
        warn("  Points found in neither model (internal error):\n" + pNone.mkString("    ", "\n    ", "\n"))
    }

    if (cEquOnly.length > 0 || cComOnly.length > 0 || cMultiple.length > 0 || cNone.length > 0) {
      spacer
      warn("CONTROL WARNINGS:")
      if (cEquOnly.length > 0)
        warn("  Controls found in equipmentModel only:\n" + cEquOnly.mkString("    ", "\n    ", "\n"))
      if (cComOnly.length > 0)
        warn("  Controls found in communicationsModel only:\n" + cComOnly.mkString("    ", "\n    ", "\n"))
      if (cMultiple.length > 0)
        warn("  Controls found in both models more than once:\n" + cMultiple.mkString("    ", "\n    ", "\n"))
      if (cNone.length > 0)
        warn("  Controls found in neither model (internal error):\n" + cNone.mkString("    ", "\n    ", "\n"))
    }

    val pWarnings = points.values.filterNot(_.warnings.isEmpty)
    if (!pWarnings.isEmpty) {
      spacer
      pWarnings.foreach(p =>
        warn("WARNINGS for Point '" + p.name + "':\n    " + p.warnings.mkString("\n    ")))
    }

    val cWarnings = controls.values.filterNot(_.warnings.isEmpty)
    if (!cWarnings.isEmpty) {
      spacer
      cWarnings.foreach(c =>
        warn("WARNINGS for Control '" + c.name + "':\n    " + c.warnings.mkString("\n    ")))
    }

    valid
  }

}

class CacheType(val cache: LoadCache) {

  def reset = {
    cache.controls.clear
    cache.points.clear
  }
}

class LoadCacheEqu(override val cache: LoadCache) extends CacheType(cache) {

  def addPoint(name: String, unit: String) = {
    cache.points.get(name) match {
      case Some(p) => p.addReference(this, "", unit)
      case _ => cache.points += (name -> new CachedPoint(this, name, unit))
    }
  }
  def addControl(name: String) = {
    cache.controls.get(name) match {
      case Some(c) => c.addReference(this)
      case _ => cache.controls += (name -> new CachedControl(this, name))
    }

  }
  //override def getType = "equipmentModel"
}

class LoadCacheCom(override val cache: LoadCache) extends CacheType(cache) {

  def addPoint(endpointName: String, name: String, index: Int, unit: String = "") = {
    cache.points.get(name) match {
      case Some(p) => p.addReference(this, endpointName, unit, index)
      case _ => cache.points += (name -> new CachedPoint(this, endpointName, name, unit, index))
    }

  }
  def addControl(endpointName: String, name: String, index: Int) = {
    cache.controls.get(name) match {
      case Some(c) => c.addReference(this, endpointName, index)
      case _ => cache.controls += (name -> new CachedControl(this, endpointName, name, index))
    }

  }
}

abstract class ValidateResult
object ValidateResult {
  case object NONE extends ValidateResult
  case object COM_ONLY extends ValidateResult
  case object EQU_ONLY extends ValidateResult
  case object COMPLETE extends ValidateResult
  case object MULTIPLE extends ValidateResult // Multiple com or equ references. Is this ever good?
}

class CachedObject(referencedFrom: CacheType, val name: String) {
  var errors = List[String]()
  var warnings = List[String]()
  var comCount = if (referencedFrom.isInstanceOf[LoadCacheCom]) 1 else 0
  var equCount = if (referencedFrom.isInstanceOf[LoadCacheEqu]) 1 else 0

  def incrementReference(referencedFrom: CacheType) = {
    referencedFrom match {
      case c: LoadCacheCom => comCount += 1
      case e: LoadCacheEqu => equCount += 1
    }
  }

  def validate: ValidateResult = {
    import ValidateResult._

    if (comCount == 0 && equCount == 0) {
      NONE
    } else if (comCount == 1 && equCount == 1) {
      // Good
      COMPLETE
    } else if (comCount == 0 && equCount == 1) {
      EQU_ONLY
    } else if (comCount == 1 && equCount == 0) {
      COM_ONLY
    } else {
      MULTIPLE
    }
  }

  override def toString = name
}

class CachedPoint(
    referencedFrom: CacheType,
    var endpointName: String,
    override val name: String,
    var unit: String,
    index: Int) extends CachedObject(referencedFrom, name) {

  def this(referencedFrom: CacheType, _name: String, _unit: String) = this(referencedFrom, "", _name, _unit, -1)
  def this(referencedFrom: CacheType, _endpointName: String, _name: String, _index: Int) = this(referencedFrom, _endpointName, _name, "", _index)

  def addReference(_referencedFrom: CacheType, _endpointName: String, _unit: String = "", _index: Int = -1) = {
    if (_endpointName.length > 0) {
      if (endpointName.length > 0)
        warnings ::= "Point '" + name + "' is referenced by two endpoints: '" + endpointName + "' and '" + _endpointName + "'"
      endpointName = _endpointName
    }

    if (_unit.length > 0) {
      if (unit.length > 0 && unit != _unit && unit != "raw" && _unit != "raw")
        warnings ::= "Point '" + name + "' has two different units: '" + unit + "' and '" + _unit + "'"
      unit = _unit
    }

    incrementReference(_referencedFrom)
  }

}

class CachedControl(
    referencedFrom: CacheType,
    var endpointName: String,
    override val name: String,
    var index: Int = -1) extends CachedObject(referencedFrom, name) {

  def this(referencedFrom: CacheType, _name: String) = this(referencedFrom, "", _name)

  def addReference(referencedFrom: CacheType, _endpointName: String = "", _index: Int = -1) = {
    if (_endpointName.length > 0) {
      if (endpointName.length > 0)
        warnings ::= "Control '" + name + "' is referenced by two endpoints: '" + endpointName + "' and '" + _endpointName + "'"
      endpointName = _endpointName
    }

    if (_index >= 0) {
      if (index >= 0 && index != _index)
        warnings ::= "Control '" + name + "' has two index values in the configuration: '" + index + "' and '" + _index + "'"
      index = _index
    }

    incrementReference(referencedFrom)
  }

}
