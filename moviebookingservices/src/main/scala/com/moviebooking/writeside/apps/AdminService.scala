package com.moviebooking.writeside.apps

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.moviebooking.writeside.aggregates._
import com.moviebooking.writeside.common.{ClusterSettings, ClusterShard}
import com.moviebooking.writeside.generator.Generators

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object AdminService extends App {
  val settings                                 = new ClusterSettings(2553)
  implicit val system                          = settings.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  ClusterShard.start()

  val screenShard = ClusterShard.shardRegion(ShowActor.shardName)
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case request @ HttpRequest(POST, Uri.Path("/init"), _, _, _) =>
      val theatres: Seq[(String, Address)] = Generators.theatres
      val movies                           = Generators.movies
      val theatreFutures: Future[Seq[Any]] = initializeTheatres(theatres)
      val movieFutures: Future[List[Any]]  = initializeMovies(movies)
      val screenFutures: Future[immutable.Seq[Any]] =
        initializeScreens(movies, theatres)
      val seq = Seq(screenFutures, theatreFutures, movieFutures)
      Future
        .sequence(seq)
        .map(
          list ⇒ HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, list.toString()), status = StatusCodes.Created)
        )

  }

  Http().bindAndHandleAsync(requestHandler, settings.hostname, 8089)

  def initializeScreens(movies: List[MovieState],
                        theatres: Seq[(String, Address)])(implicit system: ActorSystem): Future[immutable.Seq[Any]] = {
    implicit val timeout: Timeout = 20.seconds
    val showTimeTheatres: immutable.Seq[(String, (String, Address))] =
      Generators.showTimes.flatMap(showTime ⇒ {
        theatres.map(theatre ⇒ (showTime, theatre))
      })

    //FIXME not all movies can be in all screens
    val tuples: immutable.Seq[((String, (String, Address)), MovieState)] =
      showTimeTheatres.flatMap(show ⇒ {
        movies.map(movie ⇒ (show, movie))
      })

    val seq: immutable.Seq[Future[Any]] = tuples.flatMap(tuple ⇒ {
      val screenShard = ClusterShard.shardRegion(ShowActor.shardName)
      val showIds     = Generators.screenIds
      val futures = showIds.map(showId ⇒ {
        val future: Future[Any] = screenShard ? InitializeShow(
          ShowId(showId, tuple._1._1, tuple._1._2._1),
          LocalTime.parse(tuple._1._1,
                          DateTimeFormatter
                            .ofPattern("HH:mm")),
          tuple._2.name,
          Generators.generateSeatMap
        )
        future
      })
      futures
    })

    Future.sequence(seq)
  }

  def initializeTheatres(theatres: Seq[(String, Address)])(implicit system: ActorSystem): Future[Seq[Any]] = {
    implicit val timeout: Timeout = 20.seconds
    val theatreShard              = ClusterShard.shardRegion(Theatre.shardName)
    val futures =
      theatres.map(tuple ⇒ theatreShard ? InitialiseTheatre(tuple._1, tuple._2))
    Future.sequence(futures)
  }

  def initializeMovies(movies: List[MovieState])(implicit system: ActorSystem): Future[List[Any]] = {
    implicit val timeout: Timeout = 20.seconds
    val movieShard                = ClusterShard.shardRegion(MovieActor.shardName)
    val futures =
      movies.map(movie ⇒ movieShard ? InitializeMovie(movie.name, movie.cast, movie.synopsis, movie.genre, movie.metadata))
    Future.sequence(futures)
  }
}
