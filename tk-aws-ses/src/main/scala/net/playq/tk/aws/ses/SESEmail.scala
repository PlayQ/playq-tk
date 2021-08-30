package net.playq.tk.aws.ses
import net.playq.tk.aws.ses.config.SESConfig
import software.amazon.awssdk.services.ses.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

final case class SESEmail private (toAddresses: Set[String], txtMessage: Option[String], htmlMsg: Option[String], subject: Option[String]) {
  def withTxt(txt: String): SESEmail = this.copy(txtMessage = Some(txt))

  def withHtml(htmlMsg: String): SESEmail = this.copy(htmlMsg = Some(htmlMsg))

  def withSubject(subject: String): SESEmail = this.copy(subject = Some(subject))

  def cook(sesConfig: SESConfig, encoding: String = "UTF-8"): SendEmailRequest = {
    val body = Body.builder
      .pipe(body => txtMessage.fold(body)(txt => body.text(Content.builder.charset(encoding).data(txt).build)))
      .pipe(body => htmlMsg.fold(body)(html => body.html(Content.builder.charset(encoding).data(html).build)))
      .build

    val message = Message.builder
      .body(body)
      .pipe(msg => subject.fold(msg)(s => msg.subject(Content.builder.charset(encoding).data(s).build)))
      .build

    SendEmailRequest.builder
      .destination(Destination.builder.toAddresses(toAddresses.asJava).build)
      .message(message)
      .source(sesConfig.senderEmail)
      .build
  }
}

object SESEmail {
  def apply(toAddress: String): SESEmail = new SESEmail(Set(toAddress), None, None, None)

  def apply(toAddresses: Set[String]): SESEmail = new SESEmail(toAddresses, None, None, None)
}
