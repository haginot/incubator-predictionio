package org.template.recommendation

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(user: String, num: Int, creationYear: Option[Int] = None)
  extends Serializable

case class PredictedResult(itemScores: Array[ItemScore]) extends Serializable

// todo(maxim.korolyov): add creation date to the recomendation result
case class ItemScore(item: String, score: Double) extends Serializable

object RecommendationEngine extends IEngineFactory {
  def apply() =
    new Engine(classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm]),
      classOf[Serving])
}
