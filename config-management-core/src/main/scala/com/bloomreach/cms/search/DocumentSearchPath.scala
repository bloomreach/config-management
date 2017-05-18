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

import com.bloomreach.cms.redis.{CMSConfig, CacheUpdater, CacheReader}
import org.json4s.JsonAST.{JField, JObject, JArray, JNothing}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import com.bloomreach.cms.path.utils.Utils
import com.bloomreach.cms.path.utils.TemplateUtils

/**
  * @author neel.choudhury
  */
case class DocumentSearchPath(val pathString: String, val cacheUpdater: CacheUpdater, cacheReader: CacheReader) extends LazyLogging {

  val DB_NAME_POS_IN_SEARCH_PATH = 0
  val DOCUMENT_NAME_POS_IN_SEARCH_PATH = 1
  val REALM_POS_IN_SEARCH_PATH = 2

  /**
    * if the realm string is "*" we get all realm for the given database
    *
    * @param realmString
    * @param dbName
    * @return
    */
  private def getRealmSetForDb(realmString : String, dbName : String): Set[String]= {

    if(realmString.equals("*")) {
      cacheReader.getAllRealmForDb(dbName)
    } else {
      Set(realmString)
    }
  }

  /**
    * if the document string is "*" we get all realm for the given database
    *
    * @param documentString
    * @param dbName
    * @return
    */
  private def getDocumentSetForDb(documentString : String, dbName : String): Set[String] = {

    if(documentString.equals("*")) {
      cacheReader.getAllDocumentNames(dbName)
    } else {
       Set(documentString)
    }
  }

  /**
    * if dbstring is "*" get all db in the database
    *
    * @param dbString
    * @return
    */
  private def getDbSet(dbString : String) : Set[String] = {
    if(dbString.equals("*")) {
      cacheReader.getAllDb
    } else {
      Set(dbString)
    }
  }

  /**
    *
    * @param cmsConfig
    * @return
    */
  def getFullDocuments(defaultValue: Option[JValue], cmsConfig: CMSConfig) : Set[FullDocument] = {

    val key = Utils.getKeyNameFromPath(cmsConfig.key)
    val res = cacheUpdater.getNestedValue(cmsConfig.getRedisKey())

    val mergedRes = (defaultValue, res) match {
      case (Some(d), Some(c)) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, c), "time taken by mergeTwoJson method: ")
      case _ => None
    }
    
    mergedRes match {
      case Some(x) => {
        x \ key match {
          case JArray(arr) =>  {
            val setOfArrayElements = arr
                                     .map( x => JObject(List( JField(key, x )) ))
                                     .toSet

            setOfArrayElements.map(FullDocument(_))
          }
          case _ => Set(FullDocument(x))
        }
      }
      // For no result return empty set
      case None => Set()
    }
  }

  /**
    *
    * @param pathList
    * @return
    */
  private def getFullDocumentSet(defaultValue: Option[JValue], pathList : Set[CMSConfig]) : Set[FullDocument]= {
    pathList.flatMap(getFullDocuments(defaultValue, _))
  }

  /**
    *
    * @param pathList
    * @return
    */
  private def getLazyDocumentSet(pathList : Set[CMSConfig]) : Set[LazyDocument]= {
    pathList.map(LazyDocument(_, cacheUpdater, cacheReader))
  }

  /**
    *
    * @param pathString
    * @return
    */
  def getDocumentList() : Set[_ <: SearchDocument] = {

    // For converting /key1/key2/key3/field/ => key1/key2/key3/field
    val splitedString = pathString.split("/").filter( ! _.isEmpty)

    // Various parameters based on the path  /key1/key2/key3/field
    val dbName = splitedString(DB_NAME_POS_IN_SEARCH_PATH)
    val documentName = splitedString(DOCUMENT_NAME_POS_IN_SEARCH_PATH)
    val realm = splitedString(REALM_POS_IN_SEARCH_PATH)

    // TODO: Support "*" in key path
    val keyPath = "config/" + splitedString.drop(REALM_POS_IN_SEARCH_PATH + 1).mkString("/")

    val dbSet = getDbSet(dbName)
    val allDocuments = dbSet.map { 
      db => {
        val realmSet = getRealmSetForDb(realm ,db)
        val documentsForRealms = realmSet.map { 
          realm => {
            val documentSet = getDocumentSetForDb(documentName, db)
            getDocumentSetFor(db, realm, documentSet, keyPath)
          }
        }.flatMap { x => x.toList } 
        documentsForRealms
      }
    }.flatMap { x => x.toList }
                          
    return allDocuments
  }
  
  /**
   * Fetches default value for a given keyPath and merges that with each of the document of the KeyPath 
   * @param db
   * @param realm
   * @param documentSet
   * @param keyPath
   * @return
   */
  def getDocumentSetFor(db: String, realm: String, documentSet: Set[String], keyPath: String): Set[_ <: SearchDocument] = {
    val cmsConfigsForConcreteValue = documentSet.map { document => CMSConfig(realm, db, document, keyPath) }
    
    // If the keyPath is only config/ then we will get a set of
    if(keyPath.equals("config/")) {
      getLazyDocumentSet(cmsConfigsForConcreteValue)
    } else {
      val cmsConfigForDefaultValue = CMSConfig(realm, db, documentSet.toList(0), keyPath)
      logger.info("cms config for default value is: " + cmsConfigForDefaultValue)
      val defaultValue = Utils.time(cacheReader.getDefaultValues(cmsConfigForDefaultValue), "time taken by getDefaultValues method: ")
      logger.debug("default value is: " + JsonMethods.compact(defaultValue.getOrElse(JNothing)))
      getFullDocumentSet(defaultValue, cmsConfigsForConcreteValue)
    }
  }
}

