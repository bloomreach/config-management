/**
 * Copyright 2016 BloomReach, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.bloomreach.cms.search

import org.json4s.JsonAST._

/**
  * @author neel.choudhury
  */
case class SearchOrderByHandler(val isOrderBy: Boolean = false, val keyToOrder: String = "") {


  /**
    * Compares two JValue by extracting the key on which to compare
    * @param json1
    * @param json2
    * @return
    */
  def compare(json1: JValue, json2: JValue): Boolean = {

    val firstObjectForComparison = json1 \\ keyToOrder
    val secondObjectForComparison = json2 \\ keyToOrder
    (firstObjectForComparison, secondObjectForComparison) match{

      case ( JInt(i), JInt(j) ) => i < j
      case ( JLong(i), JLong(j) ) => i < j
      case ( JDouble(i), JDouble(j) ) => i < j
      case ( JDecimal(i), JDecimal(j) ) => i < j
      case ( JString(i), JString(j) ) => i < j

      // In other cases we will compare only the string representation
      case (x: JValue, y: JValue) => {
        x.toString < y.toString
      }
    }
  }

  /**
    * Returns a new ordering of JValue depending on the criteria to sort
    * @param documentList
    * @return
    */
  def orderDocuments(documentList: List[JValue]) : List[JValue] = {

    isOrderBy match {
      case true => documentList.sortWith(compare)
      case false => documentList
    }

  }
}
