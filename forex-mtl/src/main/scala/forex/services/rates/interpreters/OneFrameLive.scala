package forex.services.rates.interpreters

import cats.Applicative
import cats.data.EitherT
import forex.config.ApplicationConfig
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.rates.{Algebra, errors}
import scalaj.http.Http
import io.circe.parser.decode
import io.circe.generic.auto._

import java.time.{Instant, OffsetDateTime, ZoneId}

class OneFrameLive[F[_] : Applicative](config: ApplicationConfig) extends Algebra[F] {
  private case class OneFrameResponse(from: String, to: String, bid: Double, ask: Double, price: Double, time_stamp: String) {
    def toRate: Rate = {
      val parsedTime = OffsetDateTime.ofInstant(Instant.parse(time_stamp), ZoneId.systemDefault())
      Rate(Rate.Pair(Currency.fromString(from), Currency.fromString(to)), Price(price), Timestamp(parsedTime))
    }
  }

  override def get(pair: Rate.Pair): F[Either[errors.Error, Rate]] = {
    val response = executeGetRequest(config.url.endPointUrl + "/rates", Seq(("pair", pair.combine)))
    EitherT.fromEither[F](response).value
  }

  private def executeGetRequest(uri: String, parameters: Seq[(String, String)]): Either[errors.Error, Rate] = {
    val response = try {
      Some(Http(uri).header("token", config.url.token).params(parameters).asString)
    } catch {
      case _: Exception => None
    }
    if (response.isDefined) {
      decode[List[OneFrameResponse]](response.get.body) match {
        case Left(res) =>
          Left(errors.Error.OneFrameLookupFailed(res.getMessage): errors.Error)
        case Right(res) => Right(res.head.toRate)
      }
    } else Left(errors.Error.ServiceUnavailableError("Service Rates API is down. Please try after sometime."): errors.Error)
  }
}
