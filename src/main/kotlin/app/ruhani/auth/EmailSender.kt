package app.ruhani.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

/**
 * Delivers OTP codes to the user.
 *
 * Two implementations live behind one property:
 *   - `ruhani.email.provider=log`  (default, dev) → [LoggingEmailSender]
 *     prints the code to stdout. Combined with the `000000` universal
 *     bypass, this keeps local development unblocked without needing AWS.
 *   - `ruhani.email.provider=ses`  (production) → [SesEmailSender] sends
 *     via AWS SES v2, using the default credential chain.
 */
interface EmailSender {
    fun sendOtp(toEmail: String, code: String)
}

@Component
@ConditionalOnProperty(name = ["ruhani.email.provider"], havingValue = "log", matchIfMissing = true)
class LoggingEmailSender : EmailSender {
    override fun sendOtp(toEmail: String, code: String) {
        println("[OTP] $toEmail → $code")
    }
}

@Component
@ConditionalOnProperty(name = ["ruhani.email.provider"], havingValue = "ses")
class SesEmailSender(
    @Value("\${ruhani.email.from}") private val fromAddress: String,
    @Value("\${ruhani.aws.region:us-east-2}") private val region: String,
) : EmailSender {

    private val client: SesV2Client by lazy {
        SesV2Client.builder().region(Region.of(region)).build()
    }

    override fun sendOtp(toEmail: String, code: String) {
        val subject = "Your Saathji App sign-in code"
        val bodyText = """
            |Your one-time sign-in code is:
            |
            |    $code
            |
            |It expires in 10 minutes. If you didn't request this, you can ignore the email.
        """.trimMargin()

        val request = SendEmailRequest.builder()
            .fromEmailAddress(fromAddress)
            .destination(Destination.builder().toAddresses(toEmail).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(
                                Body.builder()
                                    .text(Content.builder().data(bodyText).charset("UTF-8").build())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        client.sendEmail(request)
    }
}
