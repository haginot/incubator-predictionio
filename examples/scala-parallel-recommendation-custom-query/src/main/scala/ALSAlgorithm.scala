package org.template.recommendation

import io.prediction.controller.PAlgorithm
import io.prediction.controller.Params
import io.prediction.data.storage.BiMap

import org.apache.spark.SparkContext._
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.{Rating => MLlibRating}

import grizzled.slf4j.Logger

case class ALSAlgorithmParams(rank: Int, numIterations: Int, lambda: Double,
                              seed: Option[Long]) extends Params

/**
 * Use ALS to build item x feature matrix
 */
class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends PAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  def train(data: PreparedData): ALSModel = {
    require(!data.ratings.take(1).isEmpty,
      s"viewEvents in PreparedData cannot be empty." +
        " Please check if DataSource generates TrainingData" +
        " and Preprator generates PreparedData correctly.")
    require(!data.items.take(1).isEmpty,
      s"items in PreparedData cannot be empty." +
        " Please check if DataSource generates TrainingData" +
        " and Preprator generates PreparedData correctly.")
    // create item's String ID to integer index BiMap
    val itemStringIntMap = BiMap.stringInt(data.items.keys)
    val userStringIntMap = BiMap.stringInt(data.ratings.map(_.user))

    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) =>
      (itemStringIntMap(id), item)
    }.collectAsMap.toMap

    val mllibRatings = data.ratings.map { r =>
      // Convert user and item String IDs to Int index for MLlib
      val iindex = itemStringIntMap.getOrElse(r.item, -1)
      val uindex = userStringIntMap.getOrElse(r.user, -1)

      if (iindex == -1)
        logger.info(s"Couldn't convert nonexistent item ID ${r.item}"
          + " to Int index.")

      (uindex -> iindex) -> 1
    }.filter { case ((u, i), v) => (i != -1) && (u != -1) }
    .reduceByKey(_ + _) // aggregate all view events of same item
    .map { case ((u, i), v) =>  MLlibRating(u, i, v) }

    // MLLib ALS cannot handle empty training data.
    require(!mllibRatings.take(1).isEmpty,
      s"mllibRatings cannot be empty." +
        " Please check if your events contain valid user and item ID.")

    // seed for MLlib ALS
    val seed = ap.seed.getOrElse(System.nanoTime)

    val m = ALS.trainImplicit(
      ratings = mllibRatings,
      rank = ap.rank,
      iterations = ap.numIterations,
      lambda = ap.lambda,
      blocks = -1,
      alpha = 1.0,
      seed = seed)

    new ALSModel(productFeatures = m.productFeatures,
      itemStringIntMap = itemStringIntMap, items = items)
  }

  def predict(model: ALSModel, query: Query): PredictedResult = {
    val queryFeatures =
      model.items.filter { case (ixd, item) => filterItem(item, query) }
        .keys.map { itemId => model.productFeatures.lookup(itemId).headOption }
        .seq.flatten

    val indexScores = if (queryFeatures.isEmpty) {
      logger.info(s"No productFeatures found for query ${query}.")
      Array[(Int, Double)]()
    } else {
      model.productFeatures.mapValues { f =>
        queryFeatures.map { qf => cosine(qf, f) }.reduce(_ + _)
      }.filter(_._2 > 0) // keep items with score > 0
       .collect()
    }

    implicit val ord = Ordering.by[(Int, Double), Double](_._2)
    val topScores = getTopN(indexScores, query.num).toArray

    val itemScores = topScores.map { case (i, s) =>
      new ItemScore(item = model.itemIntStringMap(i), score = s)
    }

    new PredictedResult(itemScores)
  }

  private def getTopN[T](s: Seq[T], n: Int)
                        (implicit ord: Ordering[T]): Iterable[T] = {

    var result = List.empty[T]

    for (x <- s) {
      if (result.size < n)
        result = x :: result
      else {
        val min = result.min
        if (ord.compare(x, min) < 0) {
          result = x :: result.filter(_ != min)
        }
      }
    }

    result.sorted.reverse
  }

  private def cosine(v1: Array[Double], v2: Array[Double]): Double = {
    val size = v1.size
    var i = 0
    var n1: Double = 0
    var n2: Double = 0
    var d: Double = 0
    while (i < size) {
      n1 += v1(i) * v1(i)
      n2 += v2(i) * v2(i)
      d += v1(i) * v2(i)
      i += 1
    }
    val n1n2 = (math.sqrt(n1) * math.sqrt(n2))
    if (n1n2 == 0) 0 else (d / n1n2)
  }

  private def filterItem(item: Item, query: Query) = item.creationYear
    .map(icr => query.creationYear.forall(icr >= _))
    .getOrElse(true)
}
