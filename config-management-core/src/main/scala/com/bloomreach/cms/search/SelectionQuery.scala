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

import org.apache.commons.lang.NotImplementedException
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import com.bloomreach.cms.path.utils.Utils
import com.bloomreach.cms.path.utils.TemplateUtils

/**
 * @author amit.kumar
 * SelectionQuery class to handle different type of selections in CMS search feature
 */
abstract class SelectionQuery {
  
  /**
    * Method transforms the selection query to get the path required to get from redis
    * @param uberTemplate to refer
    * @return SelectionQuery with fully qualified fieldName
    */
  def transformSelectionQuery(uberTemplate: JValue): SelectionQuery
  
  /**
   * @return Json representation of this selectionQuery
   */
  def getJsonRepresentation(): JValue = {
    val expectedJson = this match {
      case FieldMatchingQuery(fieldName: String, value: String) => {
        val keyName = Utils.getKeyNameFromPath(fieldName)

        val jsonString =
          s"""
             |{ "$keyName" : $value }
           """.stripMargin
        JsonMethods.parse(jsonString)
      }
      case _ => throw new NotImplementedException
    }
    return expectedJson
  }
}


/**
 * This class will have a filedName and a value for selection of type fieldName:value
 *
 */
case class FieldMatchingQuery(fieldName: String, value: String) extends SelectionQuery {
  /**
    * Method transforms the selection query to get the path required to get from redis
    *
    * @param uberTemplate to refer
    * @return SelectionQuery with fully qualified fieldName
    * example: FieldMatchingQuery("customer/account/id", "1") will be converted to FieldMatchingQuery("customer/account/id", "1")
    */
  def transformSelectionQuery(uberTemplate: JValue): SelectionQuery = {
    // if the given path is a single word it expands it into full path from uber template
    val expandedKey = TemplateUtils.getFullPathFromTemplate(fieldName, uberTemplate)

    // Fot the desired value all string literals are quoted so that they can be properly transformed to json
    val quotedValue = SearchUtils.quoteStringLiteralsInJson(value)

    FieldMatchingQuery(expandedKey, quotedValue)
  }
}