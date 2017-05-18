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

import org.json4s.Diff
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import com.bloomreach.cms.redis.{CacheUpdater, CacheReader}
import com.bloomreach.cms.redis.CMSConfig
import com.typesafe.scalalogging.LazyLogging
import com.bloomreach.cms.path.utils.Utils

/**
  * @author neel.choudhury
  */

case class SearchSelectionHandler(val cacheUpdater: CacheUpdater,
                                  val cacheReader: CacheReader,
                                  val uberTemplate: JValue, 
                                  val listOfSelectionQuery : List[SelectionEntity]) extends LazyLogging {

  /**
    *
    * @param selectionList ( List of all selection entities )
    * @param universeOfDocuments ( Set of all documents )
    * @return  (Set of all document name that satisfy all selection query)
    */
  def findAllDocumentsForSelectionList(selectionList: List[SelectionEntity],
                                      universeOfDocuments : Set[_ <: SearchDocument]) : Set[_ <: SearchDocument]= {

    val resultDocuments = {
      // In case of no selection return all the documents
      if(selectionList.isEmpty) {
        universeOfDocuments;
      } else {
        //return intersectionSet
        selectionList.foldLeft(universeOfDocuments.toList)((documentsListTillNow, currentSelection) => {
          val currentListOfDocument = findAllDocumentsForSelection(currentSelection.selectionQuery, universeOfDocuments).toList
  
          currentSelection.searchPrefix match {
            case Or => documentsListTillNow.union(currentListOfDocument)
            case And => documentsListTillNow.intersect( currentListOfDocument)
          }
        }).toSet
      }
    }
    return resultDocuments
  }

  /**
    * Checks if the value obtained from the document matches with expected value
    *
    * @param key
    * @param value
    * @param retrievedJson
    * @param expectedJson
    * @return
    */
  def checkEquality(key: String, value: String,
                    retrievedJson: JObject, expectedJson: JObject) : Boolean = {

    retrievedJson \ key match{
      case JArray(arr) => {

        expectedJson \ key match {
          case JArray(expectedArray) => {
            // Checks if the expected array is a subset of returned array
            return expectedArray.map(arr.contains(_)).reduce(_ && _)
          }
          case x: JValue => {
            // if the expected value is not an array we check if the value is contained in the returned array
            return arr.contains(x)
          }
        }
      }
      // In case of boolean the API call can send 0/1 or true/false
      case JBool(bool) => {
        return bool == SearchUtils.getBooleanValueForString(value)
      }
      // In all other cases try to match the json received from db with expected json
      case _ => {
        val Diff(changed, added, deleted) = retrievedJson.diff(expectedJson)

        if(changed == JNothing && added == JNothing && deleted == JNothing)
          return true
        else
          return false
      }
    }
  }
  
  /**
   * get map of unique cms configs to default values
   * @param selectionQry
   * @param universeOfDocument
   * @return
   */
  def getAllConfigsToDefaultValuesForLayDocuments(selectionQry: SelectionQuery,
                                   universeOfDocument : Set[_ <: SearchDocument]): Map[String, Option[JValue]] = {
    
    val allLazyDocuments = SearchUtils.getAllLazyDocuments(universeOfDocument).map { _.asInstanceOf[LazyDocument] }
    
    val allKeysToConfigs = selectionQry match {
      case FieldMatchingQuery(fieldName: String, value: String) => SearchUtils.getUniqueKeyToConfigMapForLazyDocuments(allLazyDocuments, fieldName)
    }
    
    logger.info("unique configs are: " + allKeysToConfigs)
    val allConfigsToDefaultValues = allKeysToConfigs.map { KV => (KV._1, cacheReader.getDefaultValues(KV._2)) }
    return allConfigsToDefaultValues
  }
  
  /**
    * Returns set of all document that satisfies the criteria of a selection
    * @param selectionQry
    * @param universeOfDocument
    * @return
    */

  def findAllDocumentsForSelection(selectionQry: SelectionQuery,
                                   universeOfDocument : Set[_ <: SearchDocument] ) : Set[ _ <: SearchDocument] = {

    val expectedJson = selectionQry.getJsonRepresentation()
    val allConfigsToDefaultValues = getAllConfigsToDefaultValuesForLayDocuments(selectionQry, universeOfDocument)
    
    //filter the universe of documents for given selection query
    universeOfDocument.filter(document => {
      selectionQry match {
        // if the query is of the type FieldMatchingQuery
        case FieldMatchingQuery(fieldName: String, value: String) => {

          val keyName = Utils.getKeyNameFromPath(fieldName)
          val dataForThisDocument = document match {
            case fullDocument: FullDocument => fullDocument.getValueForKey(fieldName).getOrElse(JNothing)
            case lazyDocument: LazyDocument => {
              val searchCMSConfig = CMSConfig(lazyDocument.cmsConfig.realm, lazyDocument.cmsConfig.dbName, lazyDocument.cmsConfig.documentName, fieldName)
              val defaultValue = allConfigsToDefaultValues(List(lazyDocument.cmsConfig.dbName, lazyDocument.cmsConfig.realm, fieldName).mkString("/"))
              logger.debug("defaultValue is: " + defaultValue)
              lazyDocument.getValueForKey(defaultValue, fieldName).getOrElse(JNothing)
            }
            case _ => JNothing
          }

          if (dataForThisDocument == JNothing) false
          else checkEquality(keyName, value, dataForThisDocument.asInstanceOf[JObject], expectedJson.asInstanceOf[JObject] )
        }
        case _ => throw new NotImplementedException

      }
    })
  }
  
  /**
   * @param documentSet
   * @return the set of selected documents based on the set of selectionQueries in this SearchDocument
   */
  def getSelectedDocuments(documentSet : Set[ _ <: SearchDocument]) : Set[_ <: SearchDocument] = {

    if (documentSet.size == 0) {
      return documentSet
    } else {
      val transformedSelectionList =  {
        documentSet.toList(0) match {
          case x: LazyDocument => listOfSelectionQuery.map {
            case SelectionEntity(query, prefix) => SelectionEntity(query.transformSelectionQuery(uberTemplate), prefix)
          }
          case _ => listOfSelectionQuery
        }
      }
      return findAllDocumentsForSelectionList(transformedSelectionList, documentSet)
    }
  }
}
