/**
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
package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class SmtpConfiguration {

  @NotEmpty
  @JsonProperty
  private String host;
  
  @NotEmpty
  @JsonProperty
  private String port;
  
  @NotEmpty
  @JsonProperty
  private String auth;
  
  @NotEmpty
  @JsonProperty
  private String user;
  
  @NotEmpty
  @JsonProperty
  private String password;
  
  public String getHost() {
    return host;
  }
  
  public String getPort(){
	  return port;
  }
  
  public String getAuth(){
	  return auth;
  }
  
  public String getUser(){
	  return user;
  }
  
  public String getPassword(){
	  return password;
  }
}
