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
package org.whispersystems.textsecuregcm.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.Header;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.configuration.BBPushConfiguration;
import org.whispersystems.textsecuregcm.controllers.AccountController;
import org.whispersystems.textsecuregcm.entities.CryptoEncodingException;
import org.whispersystems.textsecuregcm.entities.EncryptedOutgoingMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static com.codahale.metrics.MetricRegistry.name;

public class GCMSender {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(org.whispersystems.textsecuregcm.util.Constants.METRICS_NAME);
  private final Meter          success        = metricRegistry.meter(name(getClass(), "sent", "success"));
  private final Meter          failure        = metricRegistry.meter(name(getClass(), "sent", "failure"));

  private final Sender sender;
  private final BBPushConfiguration bbPushConfiguration;
  private final Logger logger = LoggerFactory
			.getLogger(AccountController.class);

  public GCMSender(String apiKey, BBPushConfiguration bbPushConfiguration) {
    this.sender = new Sender(apiKey);
    this.bbPushConfiguration = bbPushConfiguration;
  }

  public String sendMessage(String gcmRegistrationId,
      EncryptedOutgoingMessage outgoingMessage)
      throws NotPushRegisteredException, TransientPushFailureException {


    if (gcmRegistrationId.toLowerCase().startsWith("bb-")) {
      send(gcmRegistrationId, outgoingMessage);
      return gcmRegistrationId;
    } else {
      try {
        Message gcmMessage = new Message.Builder()
            .addData("type", "message")
            .addData("message", outgoingMessage.serialize())
            .build();

        Result result = sender.send(gcmMessage, gcmRegistrationId, 5);

      if (result.getMessageId() != null) {
        success.mark();
        return result.getCanonicalRegistrationId();
      } else {
        failure.mark();
        if (result.getErrorCodeName().equals(Constants.ERROR_NOT_REGISTERED)) {
          throw new NotPushRegisteredException("Device no longer registered with GCM.");
        } else {
          throw new TransientPushFailureException("GCM Failed: " + result.getErrorCodeName());
        }
      }
    } catch (IOException e) {
      throw new TransientPushFailureException(e);
    }
  }
}
  // Blackberry PUSH
  public void send(String gcmRegistrationId,
      EncryptedOutgoingMessage outgoingMessage) {
    String data = "";
    try{
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
    	Calendar cal = Calendar.getInstance();
        cal.getTime();
        cal.add(Calendar.MONTH, 1);
        
    	data = "--mPsbVQo0a68eIL3OAxnm"
    	        + "\r\n"
    	        + "Content-Type: application/xml; charset=UTF-8"
    	        + "\r\n\r\n"
    	        + "<?xml version="+ "\"1.0\""+ "?>"
    	        + "<!DOCTYPE pap PUBLIC "
    	        + "\"-//WAPFORUM//DTD PAP 2.1//EN\" \"http://www.openmobilealliance.org/tech/DTD/pap_2.1.dtd\">"
    	        + "<pap>"
    	        + "<push-message push-id="
    	        + "\""+gcmRegistrationId+"\""
    	        + " deliver-before-timestamp=\""+new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(cal.getTime())+"\" source-reference=\""+bbPushConfiguration.getUser()+"\">"
    	        + "<address address-value=\"push_all\"/>"
    	        + "<quality-of-service delivery-method=\"unconfirmed\"/>"
    	        + "</push-message>" + "</pap>" + "\r\n"
    	        + "--mPsbVQo0a68eIL3OAxnm" + "\r\n"
    	        + "Content-Type: text/plain" + "\r\n" + "Push-Message-ID: "
    	        + gcmRegistrationId + "\r\n\r\n" + "{" + "\"message\""+ ":" + "\"" + outgoingMessage.serialize() + "\"" + "}" + "\r\n"
    	        + "--mPsbVQo0a68eIL3OAxnm--" + "\r\n";
      }catch(Exception e){
        e.printStackTrace();
      }

    DefaultHttpClient http = new DefaultHttpClient();
    HttpPost post = new HttpPost(
    		bbPushConfiguration.getUrl());
    UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
    		bbPushConfiguration.getUser(), bbPushConfiguration.getPassword());

    post.setHeader("User-Agent", "BlackBerry Push Service SDK/1.0");
    post.setHeader("Accept",
        "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2");
    post.setHeader("Content-Type", "multipart/related; boundary=mPsbVQo0a68eIL3OAxnm; type=application/xml");
    post.setHeader("Connection", "keep-alive");
    post.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));

    StringEntity entity = null;
	
    try {
      entity = new StringEntity(data);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    post.setEntity(entity);
    try {
      HttpResponse response = http.execute(post);
       HttpEntity entity1 = response.getEntity();

       Header[] h = post.getAllHeaders();
      
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
