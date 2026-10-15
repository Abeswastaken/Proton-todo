package com.abeswastaken.protontodo.mail

import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** Sends mail through Proton's SMTP submission server with a per-address SMTP token. */
object Mailer {
    const val HOST = "smtp.protonmail.ch"
    const val PORT = 587

    fun send(from: String, to: String, token: String, subject: String, body: String, ics: String? = null) {
        val props = Properties().apply {
            put("mail.smtp.host", HOST)
            put("mail.smtp.port", PORT.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "30000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(from, token)
        })
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(from, "Proton To-Do"))
            setRecipient(Message.RecipientType.TO, InternetAddress(to))
            setSubject(subject, "UTF-8")
            sentDate = Date()
        }
        if (ics == null) {
            msg.setText(body, "UTF-8")
        } else {
            val text = MimeBodyPart().apply { setText(body, "UTF-8") }
            val cal = MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource(ics, "text/calendar; charset=UTF-8; method=REQUEST"))
                setHeader("Content-Class", "urn:content-classes:calendarmessage")
            }
            msg.setContent(MimeMultipart("alternative").apply {
                addBodyPart(text)
                addBodyPart(cal)
            })
        }
        Transport.send(msg)
    }
}
