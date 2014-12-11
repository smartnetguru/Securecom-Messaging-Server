package org.whispersystems.textsecuregcm.tests.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.controllers.MessageController;
import org.whispersystems.textsecuregcm.controllers.ReceiptController;
import org.whispersystems.textsecuregcm.entities.MessageProtos;
import org.whispersystems.textsecuregcm.federation.FederatedClientManager;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.push.PushSender;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;

import java.util.LinkedList;
import java.util.List;

import io.dropwizard.testing.junit.ResourceTestRule;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ReceiptControllerTest  {

  private static final String SINGLE_DEVICE_RECIPIENT = "+14151111111";
  private static final String MULTI_DEVICE_RECIPIENT  = "+14152222222";

  private  final PushSender             pushSender             = mock(PushSender.class            );
  private  final FederatedClientManager federatedClientManager = mock(FederatedClientManager.class);
  private  final AccountsManager        accountsManager        = mock(AccountsManager.class       );

  private  final ObjectMapper mapper = new ObjectMapper();

  @Rule
  public final ResourceTestRule resources = ResourceTestRule.builder()
                                                            .addProvider(AuthHelper.getAuthenticator())
                                                            .addResource(new ReceiptController(accountsManager, federatedClientManager, pushSender))
                                                            .build();

  @Before
  public void setup() throws Exception {
    List<Device> singleDeviceList = new LinkedList<Device>() {{
      add(new Device(1, "foo", "bar", "baz", "isgcm", null, false, 111, null));
    }};

    List<Device> multiDeviceList = new LinkedList<Device>() {{
      add(new Device(1, "foo", "bar", "baz", "isgcm", null, false, 222, null));
      add(new Device(2, "foo", "bar", "baz", "isgcm", null, false, 333, null));
    }};

    Account singleDeviceAccount = new Account(SINGLE_DEVICE_RECIPIENT, false, singleDeviceList);
    Account multiDeviceAccount  = new Account(MULTI_DEVICE_RECIPIENT, false, multiDeviceList);

    when(accountsManager.get(eq(SINGLE_DEVICE_RECIPIENT))).thenReturn(Optional.of(singleDeviceAccount));
    when(accountsManager.get(eq(MULTI_DEVICE_RECIPIENT))).thenReturn(Optional.of(multiDeviceAccount));
  }

  @Test
  public synchronized void testSingleDeviceCurrent() throws Exception {
    ClientResponse response =
        resources.client().resource(String.format("/v1/receipt/%s/%d", SINGLE_DEVICE_RECIPIENT, 1234))
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                 .put(ClientResponse.class);

    assertThat(response.getStatus() == 204);

    verify(pushSender, times(1)).sendMessage(any(Account.class), any(Device.class), any(MessageProtos.OutgoingMessageSignal.class));
  }

  @Test
  public synchronized void testMultiDeviceCurrent() throws Exception {
    ClientResponse response =
        resources.client().resource(String.format("/v1/receipt/%s/%d", MULTI_DEVICE_RECIPIENT, 12345))
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                 .put(ClientResponse.class);

    assertThat(response.getStatus() == 204);

    verify(pushSender, times(2)).sendMessage(any(Account.class), any(Device.class), any(MessageProtos.OutgoingMessageSignal.class));
  }


}
