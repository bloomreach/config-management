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
package com.bloomreach.cms.path.utils

import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods
import org.json4s.jvalue2monadic
import org.json4s.string2JsonInput
import com.bloomreach.cms.search.FieldMatchingQuery
import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import org.slf4j.LoggerFactory

/**
 * @author amit.kumar
 * utility class to perform JsonPath operations corresponding to xPath like queries
 * reference of the used library: https://github.com/jayway/JsonPath/blob/master/README.md
 */
object APIUtils {
  val logger = LoggerFactory.getLogger(APIUtils.getClass)
  val configuration = Configuration.builder().jsonProvider(new JacksonJsonNodeJsonProvider())
                                             .mappingProvider(new JacksonMappingProvider())
                                             .build()

  def getRelativeKey(prefix: String, key: String): String = {
    val relativeKeyForUpdate = key.stripPrefix(prefix)
    return relativeKeyForUpdate
  }

  /**
   * @param searchClause
   * @param projectionAtEnd
   * @return jsonPath corresponding to searchClause
   */
  def createJsonPath(searchClause: SearchPath#SearchClausePair): String = {
    val jsonPath = searchClause.from.split("/").filter(! _.isEmpty()).map { x => "['" + x + "']" }.mkString("")
    val jsonPathForSearchClause = {
      if(searchClause.isSearch()) {
        val query = searchClause.getSelectionSet().toList(0).asInstanceOf[FieldMatchingQuery]
        "[?(@." + query.fieldName + "==" + query.value + ")]"
      } else {
        //This is array with index case
        //from api request will be to update feeds/feed[1]/feed_name
        //But in the actual storage feeds/feed[0]/feed_name should be changed
        //In the api index starts from 1 just to support the legacy CMS usecase
        "[" + (searchClause.condition.toInt -1) + "]"
      }
    }

    return jsonPath + jsonPathForSearchClause
  }

  def appendProjectionAtEndInJsonPath(jsonPath: String, projectionAtEnd: Option[String]): String = {
     val projectionJsonPath = projectionAtEnd match {
      case Some(projection) => projection.split("/").filter {! _.isEmpty() }.map { x => "['" + x + "']" }.mkString("")
      case _ => ""
    }
    return jsonPath + projectionJsonPath
  }

  /**
   * @param apiKeyPath
   * @return jsonPath corresponding to the apiKeyPath
   * e.g. key1/key2[0]/field_name will be converted to $['key1']['key2'][0]['field_name']
   */
  def createJsonPath(apiKeyPath: String): String = {
    val searchPath = new SearchPath(apiKeyPath)
    val searchClauses = searchPath.getListOfSearchClauses()
    val projectionAtEnd = searchPath.extractProjectionKey()
    val resultJsonPath = searchClauses.map { searchClause => createJsonPath(searchClause)}.mkString("")
    val jsonPathWithProjectionAtEnd = appendProjectionAtEndInJsonPath(resultJsonPath, projectionAtEnd)

    val jsonPathString = "$" + jsonPathWithProjectionAtEnd
    return jsonPathString
  }

  def updateJson(originalJson: String, jsonPathsToNewValue: Map[String, Any]): String = {
    logger.debug("json for modification is: " + originalJson)
    logger.info("keyPaths for modification: " + jsonPathsToNewValue)

    val documentContext = JsonPath.using(configuration).parse(originalJson)
    for(pathValueEntry <- jsonPathsToNewValue) {
      val jsonPath = createJsonPath(pathValueEntry._1)
      logger.info(jsonPath)
      documentContext.set(jsonPath, pathValueEntry._2)
    }
    val updatedJson = documentContext.json[JsonNode]()
    logger.debug("updated json is: " + updatedJson.toString())
    return updatedJson.toString()
  }

  /**
   * @param originalJson
   * @param keyName
   * @return the string representation of actual object that will written
   * after removing the keyName for that
   * e.g. for originalJson {"field_name": "My field"}
   * and key "field_name"
   * method will return
   * "My field"
   */
  def getValueForUpdate(originalJson: String, keyName: String): Any = {
    val documentContext = JsonPath.using(configuration).parse(originalJson)
    val searchPath = new SearchPath(keyName)
    val projectionKey = searchPath.extractProjectionKey()
    
    val finalResult = projectionKey match {
      case Some(x) => {
        val jsonPath = "$['" + keyName + "']"
        val result = JsonPath.read[Any](originalJson, jsonPath)
        result
      }
      case _ => {
        val searchClause = searchPath.getListOfSearchClauses().last
        if(searchClause.isSearch()) {
          val jsonPath = createJsonPath(keyName)
          val result = JsonPath.read[Any](originalJson, jsonPath)
          val preProcessedResult = s"""$result"""
          JsonPath.read[Any](preProcessedResult, "$[0]")
        } else {
          val jsonPath = "['" + searchClause.from + "'][0]"
          val result = JsonPath.read[Any](originalJson, jsonPath)
          result
        }
      }
    }
    return finalResult
  }
  
  def getUpdatedDefaultValue(data: JValue, defaultValue: JValue): JValue = {
    val parsedData = data.values.asInstanceOf[Map[String, Any]]
    val parsedMap = parsedData.keys.map { key => (key -> getValueForUpdate(JsonMethods.compact(data), key)) }.toMap
    val updatedDefaultValue = APIUtils.updateJson(JsonMethods.compact(defaultValue), parsedMap)
    return JsonMethods.parse(updatedDefaultValue)
  }
  
  def updateAccountIDToDefaultValue(accountIDFieldPath: String, accountID: Int, defaultValue: JValue): JValue = {
    val accountIDField = Map[String, Int](accountIDFieldPath -> accountID)
    val defaultValueWithAccountID = APIUtils.updateJson(JsonMethods.compact(defaultValue), accountIDField)
    return JsonMethods.parse(defaultValueWithAccountID)
  }
  
  /**
   * Extract result json for given path from the original json
   * @param originalJson
   * @param keyPath
   * @return
   */
  def extractResultAtPath(originalJson: Option[String], keyPath: String): Option[Any] = {
    val resultAtPath = originalJson match {
      case Some(x) => {
        val jsonPath = createJsonPath(keyPath)
        val documentContext = JsonPath.using(configuration).parse(x)
        val result = JsonPath.read[Any](x, jsonPath)
        Some(result)
      }
      case None => None
    }
    return resultAtPath
  }
  
  /**
    * Following function takes a full path and extracts the realm, documentname and key to be updates
    *
    * Eg: - "test_merchant/TEST/config/feeds/feed%5B0%5D/feed_errors/blocker_list"
    * will return PathDescriptor("test_merchant", "TEST", "config/feeds/feed%5B0%5D/feed_errors/blocker_list")
    *
    * where
    * "test_merchant" is the document
    * "TEST" is the realm
    * "config/feeds/feed%5B0%5D/feed_errors/blocker_list" is the key to be updated
    *
    * @param fullPath
    * @return a PathDescriptor(<document>, <realm>, <key>)
    */

  case class PathDescriptor(val document: String, val realm: String, val key: String);

  def getPathDescriptorFromFullPath( fullPath : String) : PathDescriptor = {

    val splitedList = fullPath.split("/").toList

    val documentName = splitedList(0)
    val realmName = splitedList(1)

    val key = splitedList.drop(2).mkString("/")

    return PathDescriptor( documentName, realmName, key )
  }

  /**
    * Find common prefix path from a list of string
    * for eg ["TEST/test_merchant/snap", "TEST/test_merchant/snap"] will return "TEST/test_merchant"
    *
    * @param list
    * @return
    */
  def commonPrefixPath( paths: List[String]): String = {

    val sortedList = paths.map(_.split("/")).sortBy(x => x.length)
    var i = 0;
    sortedList(0).takeWhile {
      x => {
        sortedList.forall(_(i) == x) && {i += 1; true}
      }
    } mkString("/")
  }
}

