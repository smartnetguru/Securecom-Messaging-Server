/**
 * Copyright (C) 2013 Open WhisperSystems
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
package org.whispersystems.textsecuregcm.storage;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.util.Util;

import java.io.Serializable;

public class Device implements Serializable {

  public static final long MASTER_ID = 1;

  @JsonProperty
  private long    id;

  @JsonProperty
  private String  authToken;

  @JsonProperty
  private String  salt;

  @JsonProperty
  private String  signalingKey;

  @JsonProperty
  private String  gcmId;

  @JsonProperty
  private String  apnId;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private int registrationId;

  @JsonProperty
  private SignedPreKey signedPreKey;

  public Device() {}

  public Device(long id, String authToken, String salt,
                String signalingKey, String gcmId, String apnId,
                boolean fetchesMessages, int registrationId,
                SignedPreKey signedPreKey)
  {
    this.id              = id;
    this.authToken       = authToken;
    this.salt            = salt;
    this.signalingKey    = signalingKey;
    this.gcmId           = gcmId;
    this.apnId           = apnId;
    this.fetchesMessages = fetchesMessages;
    this.registrationId  = registrationId;
    this.signedPreKey    = signedPreKey;
  }

  public String getApnId() {
    return apnId;
  }

  public void setApnId(String apnId) {
    this.apnId = apnId;
  }

  public String getGcmId() {
    return gcmId;
  }

  public void setGcmId(String gcmId) {
    this.gcmId = gcmId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setAuthenticationCredentials(AuthenticationCredentials credentials) {
    this.authToken = credentials.getHashedAuthenticationToken();
    this.salt      = credentials.getSalt();
  }

  public AuthenticationCredentials getAuthenticationCredentials() {
    return new AuthenticationCredentials(authToken, salt);
  }

  public String getSignalingKey() {
    return signalingKey;
  }

  public void setSignalingKey(String signalingKey) {
    this.signalingKey = signalingKey;
  }

  public boolean isActive() {
    return fetchesMessages || !Util.isEmpty(getApnId()) || !Util.isEmpty(getGcmId());
  }

  public boolean getFetchesMessages() {
    return fetchesMessages;
  }

  public void setFetchesMessages(boolean fetchesMessages) {
    this.fetchesMessages = fetchesMessages;
  }

  public boolean isMaster() {
    return getId() == MASTER_ID;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public void setRegistrationId(int registrationId) {
    this.registrationId = registrationId;
  }

  public SignedPreKey getSignedPreKey() {
    return signedPreKey;
  }

  public void setSignedPreKey(SignedPreKey signedPreKey) {
    this.signedPreKey = signedPreKey;
  }
}
