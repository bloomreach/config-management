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

import org.json4s.JsonAST.{JArray, JObject, JValue}
import com.bloomreach.cms.redis.{CacheUpdater, CacheReader}
import com.bloomreach.cms.redis.CMSConfig
import com.typesafe.scalalogging.LazyLogging
import com.bloomreach.cms.path.utils.TemplateUtils

/**
  * @author neel.choudhury
  */
case class SearchProjectionHandler(val cacheUpdater: CacheUpdater, 
                                   val cacheReader: CacheReader,
                                   val uberTemplate: JValue, 
                                   val projectionFieldSet : Set[String] ) extends LazyLogging {


  /**
    * Return a JObject containg projection from a specific Document
    * @param document
    * @param fieldNames
    * @return
    */
  def projectDocument(document: SearchDocument,
                      fieldNames: Set[String], fieldNameToDefaultValues: Map[String, Option[JValue]]): Option[JObject] = {

    val projectedValue = document match {
      case fullDocument: FullDocument => {
        fieldNames // For each field
        .flatMap(fullDocument.getValueForKey) // get the value for key (Also flatmap removes all None)
        .map(_.asInstanceOf[JObject]) // filter all data that are None
      }
      case lazyDocument: LazyDocument => {
        fieldNames // For each field
        .flatMap {
          fieldName => {
            val validKeyForDefaultValue = List(lazyDocument.cmsConfig.dbName, lazyDocument.cmsConfig.realm, fieldName).mkString("/")
            lazyDocument.getValueForKey(fieldNameToDefaultValues(validKeyForDefaultValue), fieldName) // get the value for key (Also flatmap removes all None)
          }
        }.map(_.asInstanceOf[JObject]) // filter all data that are None
      }
    } 

    // We will return None if none of the fields are present in document
    projectedValue.size match {
      case 0 => None
      case _ => Some(projectedValue.reduce(_ merge _))
    }
  }

  /**
    * Takes a set of document and set of fields which we need to extract
    * Return a JArray where each element is a JObject containing projection for each document
    * @param documentSet
    * @param fieldNames
    * @return
    */

  def projectDocumentSet(documentSet: Set[_ <: SearchDocument],
                         fieldNames: Set[String]): JArray = {

    if (documentSet.isEmpty) return JArray(List())
    
    val fieldNameToDefaultValues = getAllConfigsToDefaultValuesForLayDocuments(fieldNames, documentSet)

    val jObjectsList = documentSet // For each document
                       .flatMap(projectDocument(_, fieldNames, fieldNameToDefaultValues))  // get the jObject for all required fields
                       .map(List(_))  // convert into list
                       .fold(List())(_ ++ _) // concat all list to create a single list for jarray

    return JArray(jObjectsList)

  }

  /**
    * In some cases user just give the name of field they want to project. Feg :- account-id
    * This method gets back the original path refered by the string
    * So "account-id" will be converted to "config/account/account-id"
    * @param projectionField (a string)
    * @return (the full key path in json represented by projectionField
    */
  private def transformProjectionField(projectionField: String): String = {
    TemplateUtils.getFullPathFromTemplate(projectionField, uberTemplate)
  }

  /**
    * For given set of document the method returns a JArray containing projection of the specific fields
    * @param documentSet
    * @return
    */
  def getProjectionFromDocuments( documentSet : Set[_ <: SearchDocument]) : JArray = {


    if (documentSet.isEmpty)
      return JArray(List())

    val transformedProjectionFields =  {
      documentSet.toList(0) match {
        case x: LazyDocument => projectionFieldSet.map(transformProjectionField)
        case _ => projectionFieldSet
      }
    }
    projectDocumentSet(documentSet, transformedProjectionFields)
  }
  
  /**
   * get map of unique cms configs to default values
   * @param selectionQry
   * @param universeOfDocument
   * @return
   */
  def getAllConfigsToDefaultValuesForLayDocuments(transformedProjectionFields: Set[String],
                                   documentSet : Set[_ <: SearchDocument]): Map[String, Option[JValue]] = {
    
    val allLazyDocuments = SearchUtils.getAllLazyDocuments(documentSet).map { _.asInstanceOf[LazyDocument] }
    
    val allKeysToConfigs = transformedProjectionFields.map {
      projectionField => SearchUtils.getUniqueKeyToConfigMapForLazyDocuments(allLazyDocuments, projectionField)
    }.reduce (_ ++ _)
    
    logger.info("unique configs are: " + allKeysToConfigs)
    val allConfigsToDefaultValues = allKeysToConfigs.map { KV => (KV._1, cacheReader.getDefaultValues(KV._2)) }
    return allConfigsToDefaultValues
  }
}
