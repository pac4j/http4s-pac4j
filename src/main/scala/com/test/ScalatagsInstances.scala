package com.test

import org.http4s.{Charset, DefaultCharset, EntityEncoder, MediaType}
import org.http4s.MediaType.`text/html`
import org.http4s.headers.`Content-Type`
import scalatags.Text.TypedTag

trait ScalatagsInstances {

  implicit def htmlContentEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[TypedTag[String]] =
    contentEncoder(`text/html`)

  private def contentEncoder[C <: TypedTag[String]](mediaType: MediaType)(implicit charset: Charset = DefaultCharset): EntityEncoder[C] =
    EntityEncoder.stringEncoder(charset).contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
}

object ScalatagsInstances extends ScalatagsInstances
