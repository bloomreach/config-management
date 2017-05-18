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
package com.bloomreach.cms.redis

import org.json4s.JsonAST.{JValue, JArray, JNothing, JField}
import org.json4s.jackson.JsonMethods
import com.typesafe.scalalogging.LazyLogging
import com.bloomreach.cms.path.utils.SearchPath
import com.bloomreach.cms.search.SearchOrderByHandler
import com.bloomreach.cms.search.SelectionEntity
import com.bloomreach.cms.search.And
import com.bloomreach.cms.search.SearchDocument
import com.bloomreach.cms.search.FullDocument
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST
import com.bloomreach.cms.search.SearchHandler
import org.apache.commons.io.FileSystemUtils
import com.bloomreach.cms.path.utils.Utils
import com.bloomreach.cms.path.utils.TemplateUtils
import com.bloomreach.cms.path.utils.APIUtils
import java.net.URLDecoder
import com.bloomreach.cms.search.FieldMatchingQuery
import org.apache.commons.lang.NotImplementedException
import com.bloomreach.cms.search.Or
import com.bloomreach.cms.search.SearchQueryMetaData
import com.bloomreach.cms.search.SearchQueryMetaData
import com.bloomreach.cms.search.SearchQueryMetaData

/**
 * @author amit.kumar
 * Utility class to search document inside redis
 */
class DocumentSearcher(val cacheUpdater: CacheUpdater, 
                       val cacheReader: CacheReader,
                       val db: String, 
                       val document: String, 
                       val realm: String) extends LazyLogging {
  
  val searchHandler = new SearchHandler(cacheUpdater, cacheReader, db)
  val ArrayBeginingChar = "["
  val ArrayEndChar = "]"
  
  /**
   * @return the data represented by this model in String format
   */
  def getData(decodedKey: String): Option[String] = {
    if(decodedKey.contains(Constants.ARRAY_IDENTIFIER)) {
      getMergedDataForSelectionQuery(decodedKey)
    } else {
      getMergedPathDataForKey(decodedKey)
    }
  }
  
  /**
   * This method is to search result in json doucments stored in redis database
   * 
   * @param queryString
   * @param projectionSting
   * @param orderByString
   * @param metaData
   * @return
   * 
   * e.g. queryString = "collection:PRODUCTION+AND+status:ACTIVE?"
   *      projectionString = "fields=name,id"
   *      orderByString = "account-id"
   */
  def searchData(queryString: String, projectionString: String, orderByString: String, metaData: SearchQueryMetaData): String = {
    val (realm, listOfSelection) = getSelectionEntityList(queryString, metaData)
    
    val setOfProjection = getProjectionSet(projectionString, metaData)

    val orderByHandler = getSearchOrderByHandler(orderByString)

    val fromString = s"/$db/*/$realm"
    val jsonResult = searchHandler.searchOnPath(fromString, listOfSelection, setOfProjection, orderByHandler)

    val wrappedJArray = JObject(List(JField(Constants.WRAPPED_RESULT_ARRAY_KEY, jsonResult )))
    val wrappedJObject = JObject(List(JField(Constants.WRAPPED_RESULT_OBJECT_KEY, wrappedJArray )))

    return JsonMethods.pretty(wrappedJObject)
  }
  
  /**
   * @param db
   * @param redisKey, can be obtained after creating a CMSEntity object
   * @return
   */
  def getMergedDataForSelectionQuery(key: String): Option[String] = {
    val dataKey = List( "config", key).filter(!_.isEmpty).mkString("/")
    val cmsConfig = new CMSConfig(realm, db, document, dataKey)
   
    val defaultValues = Utils.time(cacheReader.getDefaultValues(cmsConfig), "time taken by getDefaultValues method: ")
    val concreteValue = Utils.time(getDataForSelectionQuery(key), "time taken by getNestedValue method: ")

    logger.debug("default value: " + JsonMethods.compact(defaultValues.get))
    logger.debug("concrete value: " + JsonMethods.compact(concreteValue.get))

    val mergedConfig = (defaultValues, concreteValue) match {
      case (Some(d), Some(c)) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, c), "time taken by mergeTwoJson method: ")
      case (Some(d), None) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, JNothing), "time taken by mergeTwoJson method: ")
      case (None, _) => None
    }
   
    mergedConfig match {
      case Some(x) => return Some(JsonMethods.pretty(x))
      case _ => return None
    }
  }
  
  /**
    * This method is used for keys of the form /key1/key2["id"="1"]/feed_settings
    * Internally such keys are converted into search queries
    * The portion in side of the [ ] are converted to  query parameter
    * Portion before the [ ] is used for getting the from clause for query
    * Portion after the  [ ] is used for generating the projection clause
    *
    * @param key
    * @return
    */
  def getDataForSelectionQuery(key: String): Option[JValue] = {
    try{
      //step 1: Break the key at first occurrence of [
      val beginingIndexOfSearchKey = key.indexOf(ArrayBeginingChar)
      val baseKey = key.substring(0, beginingIndexOfSearchKey)
      val searchKey = key.substring(beginingIndexOfSearchKey)
      
      //step 2: Extract json from redis for upto the first part
      val baseDocument = getMergedPathDataForKey(baseKey)
      
      //step 3: Create jsonPath for the searchKey and extract result
      val parentOfSearchkey = baseKey.split("/").last
      val pathToBeExtracted = List(parentOfSearchkey, searchKey).mkString("/")
      val resultDocument = APIUtils.extractResultAtPath(baseDocument, pathToBeExtracted)
      
      val extractedElementName = {
        if(pathToBeExtracted.endsWith(ArrayEndChar)) {
          Utils.getKeyNameFromPath(baseKey)
        } else {
          Utils.getKeyNameFromPath(searchKey)
        }
      }
      
      //step 4: return result
      resultDocument match {
        case Some(result) =>  Some(JObject(List(JField(extractedElementName, JsonMethods.parse(result.toString())))))
        case None => None
      }
    }
    catch {
      case e: Exception => return None
    }
  }
  
  /**
    * method return the data present is
    * @param key
    * @return
    */
  def getPathDataForKey(key: String): Option[JValue] = {

    val dataKey = List( "config", key).filter(!_.isEmpty).mkString("/")

    val cmsConfig = new CMSConfig(realm, db, document, dataKey)
    val receivedJson = cacheUpdater.getNestedValue(cmsConfig.getRedisKey())

    return receivedJson
  }
  
  /**
   * @param db
   * @param redisKey, can be obtained after creating a CMSEntity object
   * @return
   */
  def getMergedPathDataForKey(key: String): Option[String] = {
    val dataKey = List( "config", key).filter(!_.isEmpty).mkString("/")
    val cmsConfig = new CMSConfig(realm, db, document, dataKey)
    
    val defaultValues = Utils.time(cacheReader.getDefaultValues(cmsConfig), "time taken by getDefaultValues method: ")
    val concreteValue = Utils.time(getPathDataForKey(key), "time taken by getNestedValue method: ")
    
    logger.debug("default value: " + JsonMethods.compact(defaultValues.get))
    logger.debug("concrete value: " + JsonMethods.compact(concreteValue.get))
    
    val mergedConfig = (defaultValues, concreteValue) match {
      case (Some(d), Some(c)) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, c), "time taken by mergeTwoJson method: ")
      case (Some(d), None) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, JNothing), "time taken by mergeTwoJson method: ")
      case (None, _) => None
    }
    
    mergedConfig match {
      case Some(x) => return Some(JsonMethods.pretty(x))
      case _ => return None
    }
  }
  
  /**
    * Converts a query string into set of selection queries
    * Sample query string will be like
    * collection:TEST+AND+analytics:false+AND+dc:ec2e

    * + is the "query separator" which separates different query
    * AND is the prefix operator ie the result for the selection following it
    * will be AND ed with result of all selection left of it
    * We will prepend a AND operator in fro: is the key value separator in each query
    * "collection" is the key describing realm
    * @param queryString
    * @return a tuple of (realm, set_of_selection_queries
    */
  def getSelectionEntityList(queryString: String, metaData: SearchQueryMetaData): (String, List[SelectionEntity]) = {
    
    val newQueryString = "AND+" + queryString

    val listOfPrefixAndQuery = newQueryString.split(metaData.querySeparator).toList.grouped(2).toList
    logger.info("following are the prefix and query pairs: " + listOfPrefixAndQuery.toString)

    val listOfSelectionEntity = listOfPrefixAndQuery.map{
      list =>  {
        val prefix = list(0) match {
          case "AND" => And
          case "OR" => Or
          case _ => throw new NotImplementedException
        }
        val queryList = list(1).split(metaData.queryKeyValueSeparator).toList
        val queryField = URLDecoder.decode(queryList(0), "UTF-8")
        val queryValue = URLDecoder.decode(queryList(1), "UTF-8")

        SelectionEntity(FieldMatchingQuery(queryField,queryValue), prefix)
      }
    }
    logger.info("list of selection entities: " + listOfSelectionEntity.toString)

    val (realmList, selectionList) = listOfSelectionEntity.partition(e => e match {
      case SelectionEntity(selectionQuery, _) => {
        selectionQuery match {
          case FieldMatchingQuery(f, v) if f == metaData.realmKey => true
          case _ => false
        }
      }
      case _ => false
    })

    logger.info("realms: " + realmList)
    logger.info("selection entities: " + selectionList.toString)
    val realm = {
      if(realmList.size == 1){
        realmList(0).selectionQuery match {
          case FieldMatchingQuery(field, value) => value
          case _ => throw new NotImplementedException
        }
      }else {
        metaData.wildcard
      }
    }

    return (realm, selectionList)
  }
  
  /**
    * Converts a projection string into set of projections(strings)
    * sample projection string will be "merchant,account-id,analytics"
    * "," is the separator between different projection column
    * @param projectionString
    * @return Set of projections
    */
  def getProjectionSet(projectionString: String, metadata: SearchQueryMetaData): Set[String] = {
    val setOfProjection = projectionString
                          .split(metadata.projectionSeparator)
                          .filter( ! _.isEmpty)        // avoiding any empty projection
                          .toSet

    logger.info("Projection Set :" + setOfProjection.toString)

    return setOfProjection
  }
  
  def getSearchOrderByHandler(orderByString: String) : SearchOrderByHandler = {

    orderByString match {
      case "" => SearchOrderByHandler(false, "")
      case _ => SearchOrderByHandler(true, orderByString)
    }
  }
}