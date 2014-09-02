/**
 * Copyright (C) 2013 Open WhisperSystems
 * Copyright (C) 2014 Securecom
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.util;

import org.whispersystems.textsecuregcm.configuration.SmtpConfiguration;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Util {

	public static byte[] getContactToken(String number) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			byte[] result = digest.digest(number.getBytes());
			byte[] truncated = Util.truncate(result, 10);

			return truncated;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	public static String getEncodedContactToken(String number) {
		return Base64.encodeBytesWithoutPadding(getContactToken(number));
	}

	public static boolean isValidNumber(String number) {
		return number.matches("^\\+[0-9]{10,}");
	}

	public static String encodeFormParams(Map<String, String> params) {
		try {
			StringBuffer buffer = new StringBuffer();

			for (String key : params.keySet()) {
				buffer.append(String.format("%s=%s",
						URLEncoder.encode(key, "UTF-8"),
						URLEncoder.encode(params.get(key), "UTF-8")));
				buffer.append("&");
			}

			buffer.deleteCharAt(buffer.length() - 1);
			return buffer.toString();
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	public static boolean isEmpty(String param) {
		return param == null || param.length() == 0;
	}

	public static byte[] combine(byte[] one, byte[] two, byte[] three,
			byte[] four) {
		byte[] combined = new byte[one.length + two.length + three.length
				+ four.length];
		System.arraycopy(one, 0, combined, 0, one.length);
		System.arraycopy(two, 0, combined, one.length, two.length);
		System.arraycopy(three, 0, combined, one.length + two.length,
				three.length);
		System.arraycopy(four, 0, combined, one.length + two.length
				+ three.length, four.length);

		return combined;
	}

	public static byte[] truncate(byte[] element, int length) {
		byte[] result = new byte[length];
		System.arraycopy(element, 0, result, 0, result.length);

		return result;
	}

	public static boolean sendEmail(final SmtpConfiguration smtp,
			String receipient, String subject, String body) {
		Properties props = new Properties();
		Session session = null;
		props.put("mail.smtp.host", smtp.getHost());
		props.put("mail.smtp.socketFactory.port", smtp.getPort());
		props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.auth", smtp.getAuth());
		props.put("mail.smtp.port", smtp.getPort());

		if (smtp.getAuth().equalsIgnoreCase("true")) {
			session = Session.getDefaultInstance(props,
					new javax.mail.Authenticator() {
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(smtp.getUser(),
									smtp.getPassword());
						}
					});
		} else if (smtp.getAuth().equalsIgnoreCase("false")) {
			session = Session.getDefaultInstance(props);

		}

		try {

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(smtp.getUser()));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(receipient));
			message.setSubject(subject);
			message.setText(body);
			Transport.send(message);

		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

}
