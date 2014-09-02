/**
 * Copyright (C) 2014 Open Whisper Systems
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
import com.google.common.base.Optional;
import org.whispersystems.textsecuregcm.entities.PreKeyCount;
import org.whispersystems.textsecuregcm.federation.FederatedClientManager;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.KeyRecord;
import org.whispersystems.textsecuregcm.storage.Keys;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import io.dropwizard.auth.Auth;

public class KeysController {

  protected final RateLimiters           rateLimiters;
  protected final Keys                   keys;
  protected final AccountsManager        accounts;
  protected final FederatedClientManager federatedClientManager;

  public KeysController(RateLimiters rateLimiters, Keys keys, AccountsManager accounts,
                        FederatedClientManager federatedClientManager)
  {
    this.rateLimiters           = rateLimiters;
    this.keys                   = keys;
    this.accounts               = accounts;
    this.federatedClientManager = federatedClientManager;
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PreKeyCount getStatus(@Auth Account account) {
    int count = keys.getCount(account.getNumber(), account.getAuthenticatedDevice().get().getId());

    if (count > 0) {
      count = count - 1;
    }

    return new PreKeyCount(count);
  }

  protected TargetKeys getLocalKeys(String number, String deviceIdSelector)
      throws NoSuchUserException
  {
    Optional<Account> destination = accounts.get(number);

    if (!destination.isPresent() || !destination.get().isActive()) {
      throw new NoSuchUserException("Target account is inactive");
    }

    try {
      if (deviceIdSelector.equals("*")) {
        Optional<List<KeyRecord>> preKeys = keys.get(number);
        return new TargetKeys(destination.get(), preKeys);
      }

      long             deviceId     = Long.parseLong(deviceIdSelector);
      Optional<Device> targetDevice = destination.get().getDevice(deviceId);

      if (!targetDevice.isPresent() || !targetDevice.get().isActive()) {
        throw new NoSuchUserException("Target device is inactive.");
      }

      Optional<List<KeyRecord>> preKeys = keys.get(number, deviceId);
      return new TargetKeys(destination.get(), preKeys);
    } catch (NumberFormatException e) {
      throw new WebApplicationException(Response.status(422).build());
    }
  }


  public static class TargetKeys {
    private final Account         destination;
    private final Optional<List<KeyRecord>> keys;

    public TargetKeys(Account destination, Optional<List<KeyRecord>> keys) {
      this.destination = destination;
      this.keys        = keys;
    }

    public Optional<List<KeyRecord>> getKeys() {
      return keys;
    }

    public Account getDestination() {
      return destination;
    }
  }
}
