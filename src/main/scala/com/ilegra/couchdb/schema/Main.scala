package com.ilegra.couchdb.schema

import java.net.InetSocketAddress

import scala.annotation.varargs

import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.handler.codec.http.HttpVersion

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.io.Charsets
import com.twitter.logging.Logger
import com.twitter.util.Await
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.TimeoutException
import com.typesafe.config.ConfigFactory

trait Config {
  val conf = ConfigFactory.load()
  val host: String = conf.getString("host")
  val port: Int = conf.getInt("port")
}

trait HttpModule {
  private val httpClient: HttpClient = new HttpClient
  def Http[T](f: HttpClient => T): T = {
    val res = f(httpClient)
    httpClient.close
    res
  }

}

class HttpClient extends Config {
  import java.net.InetSocketAddress
  import com.twitter.finagle.http.Http
  import com.twitter.finagle.Service
  import com.twitter.finagle.builder.ClientBuilder

  private val logger: Logger = Logger(this.getClass.getName)

  private val clientWithoutErrorHandling: Service[HttpRequest, HttpResponse] = ClientBuilder()
    .codec(Http())
    .hosts(new InetSocketAddress(host, port))
    .hostConnectionLimit(1)
    .build()

  private val client: Service[HttpRequest, HttpResponse] = clientWithoutErrorHandling

  def Get(path: String): Future[HttpResponse] = {
    val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
    client(request)
  }

  def close = client.close()
}

trait JsonModule {
  val json: JsonSerialization = new JsonSerialization
}

class JsonSerialization {
  import com.fasterxml.jackson.databind.{ ObjectMapper }
  import com.fasterxml.jackson.module.scala.DefaultScalaModule

  val mapper = new ObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def read[T](json: String, clazz: Class[T]): T = mapper.readValue(json, clazz)
}

object Main extends App with HttpModule with JsonModule {
  Http { httpClient =>
    val logger: Logger = Logger(this.getClass.getName)

    val response = httpClient.Get("/_all_dbs")

    val res: Future[Seq[String]] = response map { response =>
      json.read(response.getContent.toString(Charsets.Utf8), classOf[Seq[String]]).filter { _.charAt(0) != '_' }
    } flatMap { dbs =>
      Future.collect(
        dbs.map { db =>
          httpClient.Get(s"/$db/_all_docs?startkey=%22_design%2F%22&endkey=%22_design0%22&include_docs=true") map { stream =>
            val designDoc = stream.getContent.toString(Charsets.Utf8)
            logger.info(designDoc)
            designDoc
          }
        })
    }

    Await.ready(res, Duration.fromSeconds(5))
  }
}

