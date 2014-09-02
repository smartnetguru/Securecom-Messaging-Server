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
package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.auth.AuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.sms.SmsSender;
import org.whispersystems.textsecuregcm.sms.TwilioSmsSender;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.PendingAccountsManager;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.VerificationCode;
import org.whispersystems.textsecuregcm.configuration.SmtpConfiguration;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import io.dropwizard.auth.Auth;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Path("/v1/accounts")
public class AccountController {
	
	static final String VALID_EMAIL_FORMAT = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";
	static final String EMAIL_INVITATION_BODY_TEXT = "To install Securecom messaging app, please check out out Android Playstore link at "+"https://play.google.com/store/apps/details?id=com.securecomcode.messaging&hl=en";
	static final String EMAIL_INVITATION_SUBJECT_TEXT = "Securecom messaging Invitation from ";
	static final String SMS_INVITATION_TEXT_PART1 = "You have received a Securecom messaging install invitation from your friend ";
	static final String SMS_INVITATION_TEXT_PART2 = " , to join them in private and secure messaging, please install: "+"https://play.google.com/store/apps/details?id=com.securecomcode.messaging&hl=en";
	static final String EMAIL_VERIFICATION_SUBJECT = "Securecom messaging Registration Verification Code";
	static final String EMAIL_VERIFICATION_BODY_TEXT = "Your Securecom messaging verification code is:\t";

	private final Logger logger = LoggerFactory
			.getLogger(AccountController.class);

	private final PendingAccountsManager pendingAccounts;
	private final AccountsManager accounts;
	private final RateLimiters rateLimiters;
	private final SmsSender smsSender;
	private final SmtpConfiguration smtp;

	public AccountController(PendingAccountsManager pendingAccounts,
			AccountsManager accounts, RateLimiters rateLimiters,
			SmsSender smsSenderFactory, SmtpConfiguration smtp) {
		this.pendingAccounts = pendingAccounts;
		this.accounts = accounts;
		this.rateLimiters = rateLimiters;
		this.smsSender = smsSenderFactory;
		this.smtp = smtp;
	}

	@Timed
	@GET
	@Path("/{transport}/code/{number}")
	public Response createAccount(@PathParam("transport") String transport,
			@PathParam("number") String number) throws IOException,
			RateLimitExceededException {
		if (!transport.equalsIgnoreCase("email")) {
			if (!Util.isValidNumber(number)) {
				logger.debug("Invalid number: " + number);
				throw new WebApplicationException(Response.status(400).build());
			}
		} else {
			if(!number.trim().matches(VALID_EMAIL_FORMAT)){
				throw new WebApplicationException(Response.status(400).build());
			}
		}

		switch (transport) {
		case "sms":
			rateLimiters.getSmsDestinationLimiter().validate(number);
			break;
		case "voice":
			rateLimiters.getVoiceDestinationLimiter().validate(number);
			break;
		case "email":
			rateLimiters.getEmailDestinationLimiter().validate(number);
			break;
		default:
			throw new WebApplicationException(Response.status(422).build());
		}

		VerificationCode verificationCode = generateVerificationCode();
		pendingAccounts.store(number, verificationCode.getVerificationCode());

		if (transport.equals("sms")) {
			smsSender.deliverSmsVerification(number,
					verificationCode.getVerificationCodeDisplay());
		} else if (transport.equals("voice")) {
			smsSender.deliverVoxVerification(number,
					verificationCode.getVerificationCodeSpeech());
		} else if (transport.equals("email")) {
			Util.sendEmail(smtp, number, EMAIL_VERIFICATION_SUBJECT, EMAIL_VERIFICATION_BODY_TEXT+verificationCode.getVerificationCode());
		}

		return Response.ok().build();
	}

	@Timed
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/code/{verification_code}")
	public void verifyAccount(
			@PathParam("verification_code") String verificationCode,
			@HeaderParam("Authorization") String authorizationHeader,
			@Valid AccountAttributes accountAttributes)
			throws RateLimitExceededException {
		try {
			AuthorizationHeader header = AuthorizationHeader
					.fromFullHeader(authorizationHeader);
			String number = header.getNumber();
			String password = header.getPassword();
			

			rateLimiters.getVerifyLimiter().validate(number);

			Optional<String> storedVerificationCode = pendingAccounts
					.getCodeForNumber(number);

			if (!storedVerificationCode.isPresent()
					|| !verificationCode.equals(storedVerificationCode.get())) {
				throw new WebApplicationException(Response.status(403).build());
			}

			if (accounts.isRelayListed(number)) {
				throw new WebApplicationException(Response.status(417).build());
			}

			Device device = new Device();
			device.setId(Device.MASTER_ID);
			device.setAuthenticationCredentials(new AuthenticationCredentials(
					password));
			device.setSignalingKey(accountAttributes.getSignalingKey());
			device.setFetchesMessages(accountAttributes.getFetchesMessages());
			device.setRegistrationId(accountAttributes.getRegistrationId());

			Account account = new Account();
			account.setNumber(number);
			account.setSupportsSms(accountAttributes.getSupportsSms());
			account.addDevice(device);

			accounts.create(account);

			pendingAccounts.remove(number);

			logger.debug("Stored device...");
		} catch (InvalidAuthorizationHeaderException e) {
			logger.info("Bad Authorization Header", e);
			throw new WebApplicationException(Response.status(401).build());
		}
	}

	@Timed
	@PUT
	@Path("/gcm/")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setGcmRegistrationId(@Auth Account account,
			@Valid GcmRegistrationId registrationId) {
		Device device = account.getAuthenticatedDevice().get();
		device.setApnId(null);
		device.setGcmId(registrationId.getGcmRegistrationId());
		accounts.update(account);
	}

	@Timed
	@DELETE
	@Path("/gcm/")
	public void deleteGcmRegistrationId(@Auth Account account) {
		Device device = account.getAuthenticatedDevice().get();
		device.setGcmId(null);
		accounts.update(account);
	}

	@Timed
	@PUT
	@Path("/apn/")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setApnRegistrationId(@Auth Account account,
			@Valid ApnRegistrationId registrationId) {
		Device device = account.getAuthenticatedDevice().get();
		device.setApnId(registrationId.getApnRegistrationId());
		device.setGcmId(null);
		accounts.update(account);
	}

	@Timed
	@DELETE
	@Path("/apn/")
	public void deleteApnRegistrationId(@Auth Account account) {
		Device device = account.getAuthenticatedDevice().get();
		device.setApnId(null);
		accounts.update(account);
	}

	@Timed
	@POST
	@Path("/voice/twiml/{code}")
	@Produces(MediaType.APPLICATION_XML)
	public Response getTwiml(@PathParam("code") String encodedVerificationText) {
		return Response
				.ok()
				.entity(String.format(TwilioSmsSender.SAY_TWIML,
						encodedVerificationText)).build();
	}

	@Timed
	@GET
	@Path("/invitation/{invitecontact}")
	public Response sendInvitation(@PathParam("invitecontact") String invitecontact, @Auth Account account) throws IOException,
			RateLimitExceededException {
		String sendercontact = account.getNumber();
		
		if(sendercontact == null || sendercontact.equalsIgnoreCase("")){
			throw new WebApplicationException(Response.status(400).build()); 
		}
		
		if(!invitecontact.trim().matches(VALID_EMAIL_FORMAT)){
			if(!Util.isValidNumber(invitecontact)){
				throw new WebApplicationException(Response.status(400).build());
			}
			
			smsSender.deliverSmsInvitation(invitecontact,
					SMS_INVITATION_TEXT_PART1+sendercontact+SMS_INVITATION_TEXT_PART2);
			return Response.ok().build();
		}
		
		
		if(!invitecontact.trim().matches(VALID_EMAIL_FORMAT)){
			throw new WebApplicationException(Response.status(400).build());
		}
		

		Util.sendEmail(smtp, invitecontact, EMAIL_INVITATION_SUBJECT_TEXT+sendercontact, EMAIL_INVITATION_BODY_TEXT);
		
		return Response.ok().build();
	}
	
	@VisibleForTesting
	protected VerificationCode generateVerificationCode() {
		try {
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			int randomInt = 100000 + random.nextInt(900000);
			return new VerificationCode(randomInt);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}
}
