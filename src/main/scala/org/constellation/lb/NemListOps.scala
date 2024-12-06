package org.constellation.lb

import cats.data.{NonEmptyList, NonEmptyMap, NonEmptySet}

import scala.collection.immutable.{SortedMap, SortedSet}

object NemListOps {

  /**
    * Extract new keys from the map entries
    * @param nem map of lists
    * @param v2k value to key
    * @tparam K
    * @tparam V
    * @return new keys
    */
  def extractNewKeys[K: Ordering, V](nem: NonEmptyMap[K, List[V]])(v2k: V => K): SortedSet[K] =
    nem.toNel.foldLeft(SortedSet.empty[K])(
      (acc, bcc) =>
        bcc match {
          case (_, hosts) => acc ++ hosts.map(v2k).filterNot(nem(_).isDefined)
          case _          => acc
        }
    )

  /**
    * Split a map of entries into two maps: one with the entries where all elements of the list satisfy a property and one when not
    * @param map map of lists
    * @param prop property that all elements of the list must satisfy
    * @tparam K
    * @tparam V
    * @return (map of elements that satisfy the property, map of elements that don't)
    */
  def splitByElementProp[K, V](map: Map[K, List[V]])(prop: V => Boolean): (Map[K, List[V]], Map[K, List[V]]) =
    map.partition { case (_, v) => v.forall(prop) }

  /**
    * Finds the first element of the lists in the map that satisfies a property
    * @param map map of option lists
    * @tparam K
    * @tparam V
    * @return first element wrapped in Some if found or None
    */
  def findFirstElement[K, V](map: Map[K, List[V]]): Option[V] = {
    map.collectFirst { case (_, e :: _) => e }
  }

  /**
    * Returns a map with only the keys specified in validKeys
    *
    * @param validKeys sets with keys to keep
    * @param map map to filter
    * @tparam K keys
    * @tparam V values
    * @return filtered map
    */
  def filterKeys[K, V](validKeys: NonEmptySet[K], map: NonEmptyMap[K, V]): SortedMap[K, V] =
    map.toSortedMap.filter { case (k, _) => validKeys.contains(k) }

  /**
    * "matrix-like" rotation of the map, creating a new map based regrouping by the keys extracted from the entry lists.
    * @param map map of lists
    * @param v2k value to key
    * @tparam K
    * @tparam V
    * @return map with lists from entries.
    */
  def rotateMap[K, V](map: NonEmptyMap[K, List[V]])(v2k: V => K): Map[K, List[V]] =
    map.toNel
      .collect {
        case (_, el) => el
      }
      .flatten
      .groupBy(v2k)

  /**
    * Convert a option non empty list to list
    * @param l list of t
    * @tparam T element type
    * @return option non empty list
    */
  def oNel2List[T](onel: Option[NonEmptyList[T]]): List[T] = onel match { // onel.fold(List[T]())(_.toList)
    case Some(nel) => nel.toList
    case _         => List()
  }

  /**
    * Convert a list to option non empty list
    * @param l list of t
    * @tparam T element type
    * @return option non empty list
    */
  def list2oNel[T](l: List[T]): Option[NonEmptyList[T]] = l match {
    case x :: xs => Some(NonEmptyList(x, xs))
    case _       => None
  }

}
