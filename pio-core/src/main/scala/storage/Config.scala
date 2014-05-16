package io.prediction.storage

import scala.collection.JavaConversions._

import com.mongodb.casbah.Imports._
import com.typesafe.config._

/**
 * Configuration accessors.
 *
 * This class ensures its users that the config is free of error, and provides default values as necessary.
 */
class Config {
  private val config = ConfigFactory.load()

  /** The base directory of PredictionIO deployment/repository. */
  val base: String = config.getString("io.prediction.base")

  /** The database type that stores PredictionIO settings. */
  val settingsDbType: String = config.getString("io.prediction.commons.settings.db.type")

  /** The database host that stores PredictionIO settings. */
  val settingsDbHost: String = settingsDbType match {
    case dbTypeMongoDb => try { config.getString("io.prediction.commons.settings.db.host") } catch { case _: Throwable => "127.0.0.1" }
  }

  /** The database port that stores PredictionIO settings. */
  val settingsDbPort: Int = settingsDbType match {
    case dbTypeMongoDb => try { config.getInt("io.prediction.commons.settings.db.port") } catch { case _: Throwable => 27017 }
  }

  /** The database name that stores PredictionIO settings. */
  val settingsDbName: String = settingsDbType match {
    case dbTypeMongoDb => try { config.getString("io.prediction.commons.settings.db.name") } catch { case _: Throwable => "predictionio" }
  }

  /** The database type that stores PredictionIO appdata. */
  val appdataDbType: String = config.getString("io.prediction.commons.appdata.db.type")

  /** The database host that stores PredictionIO appdata. */
  val appdataDbHost: String = appdataDbType match {
    case dbTypeMongoDb => try { config.getString("io.prediction.commons.appdata.db.host") } catch { case _: Throwable => "127.0.0.1" }
  }

  /** The database port that stores PredictionIO appdata. */
  val appdataDbPort: Int = appdataDbType match {
    case dbTypeMongoDb => try { config.getInt("io.prediction.commons.appdata.db.port") } catch { case _: Throwable => 27017 }
  }

  /** The database name that stores PredictionIO appdata. */
  val appdataDbName: String = appdataDbType match {
    case dbTypeMongoDb => try { config.getString("io.prediction.commons.appdata.db.name") } catch { case _: Throwable => "predictionio_appdata" }
  }

  /** The database user that stores PredictionIO appdata. */
  val appdataDbUser: Option[String] = try { Some(config.getString("io.prediction.commons.appdata.db.user")) } catch { case _: Throwable => None }

  /** The database password that stores PredictionIO appdata. */
  val appdataDbPassword: Option[String] = try { Some(config.getString("io.prediction.commons.appdata.db.password")) } catch { case _: Throwable => None }

    /** If appdataDbType is "mongodb", this will contain a Some[MongoDB] object. */
  val appdataMongoDb: Option[MongoDB] = if (appdataDbType == "mongodb") {
    val db = MongoClient(appdataDbHost, appdataDbPort)(appdataDbName)
    appdataDbUser map { db.authenticate(_, appdataDbPassword.getOrElse("")) }
    Some(db)
  } else None

    /** Obtains an ItemTrends object with configured backend type. */
  def getAppdataItemTrends(): ItemTrends = {
    appdataDbType match {
      case "mongodb" => {
        new MongoItemTrends(appdataMongoDb.get)
      }
      case _ => throw new RuntimeException("Invalid appdata database type: " + appdataDbType)
    }
  }
}