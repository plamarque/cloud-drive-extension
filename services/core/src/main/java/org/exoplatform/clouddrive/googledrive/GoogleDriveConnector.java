/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.clouddrive.googledrive;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.exoplatform.clouddrive.CloudDrive;
import org.exoplatform.clouddrive.CloudDriveConnector;
import org.exoplatform.clouddrive.CloudDriveException;
import org.exoplatform.clouddrive.CloudProvider;
import org.exoplatform.clouddrive.CloudUser;
import org.exoplatform.clouddrive.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;
import com.google.api.services.oauth2.model.Userinfo;

/**
 * GoogleDrive connector implementation. Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: GoogleDriveConnector.java 00000 Sep 13, 2012 pnedonosko $
 */
public class GoogleDriveConnector extends CloudDriveConnector {
  
  /**
   * API builder.
   */
  class API {
    String code;

    String userId, refreshToken, accessToken;

    long   expirationTime;

    /**
     * Authenticate to the API with OAuth2 code returned on callback url.
     * 
     * @param code String
     * @return this API
     */
    API auth(String code) {
      this.code = code;
      return this;
    }

    /**
     * Authenticate to the API with locally stored tokens.
     * 
     * @param userId
     * @param refreshToken
     * @param accessToken
     * @param expirationTime
     * @return this API
     */
    API load(String userId, String refreshToken, String accessToken, long expirationTime) {
      this.userId = userId;
      this.refreshToken = refreshToken;
      this.accessToken = accessToken;
      this.expirationTime = expirationTime;
      return this;
    }

    /**
     * Build API.
     * 
     * @return {@link GoogleDriveAPI}
     * @throws GoogleDriveException if error happen during communication with Google Drive services
     * @throws CloudDriveException if cannot load local tokens
     */
    GoogleDriveAPI build() throws GoogleDriveException, CloudDriveException {
      if (code != null && code.length() > 0) {
        // build API based on OAuth2 code
        return new GoogleDriveAPI(getClientId(), getClientSecret(), code, getProvider().getRedirectUrl());
      } else {
        // build API based on locally stored tokens
        return new GoogleDriveAPI(getClientId(),
                                  getClientSecret(),
                                  userId,
                                  accessToken,
                                  refreshToken,
                                  expirationTime);
      }
    }
  }

  /**
   * Single user credentials store. Should be used per session as transient store.
   */
  class UserCredentialStore implements CredentialStore {

    String id;

    String accessToken;

    String refreshToken;

    long   expirationTime;

    UserCredentialStore(String id, String accessToken, String refreshToken, long expirationTime) {
      this.id = id;
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.expirationTime = expirationTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean load(String userId, Credential credential) throws IOException {
      if (id.equals(userId)) {
        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setExpirationTimeMilliseconds(expirationTime);
        return true;
      }
      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void store(String userId, Credential credential) throws IOException {
      if (id.equals(userId)) {
        accessToken = credential.getAccessToken();
        refreshToken = credential.getRefreshToken();
        expirationTime = credential.getExpirationTimeMilliseconds();
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String userId, Credential credential) throws IOException {
      if (id.equals(userId)) {
        id = refreshToken = accessToken = null;
        expirationTime = 0;
      }
    }
  }

  public static final String CONFIG_PROVIDER_CLIENT_ID     = "provider-client-id";

  public static final String CONFIG_PROVIDER_CLIENT_SECRET = "provider-client-secret";

  public static final String AUTH_URL_USERHOST             = "__USERHOST__";

  // protected static final String SCOPES =
  // "https://www.googleapis.com/auth/userinfo.email+https://www.googleapis.com/auth/userinfo.profile+https://www.googleapis.com/auth/drive.readonly";

  /**
   * GoogleDrive connector plugin constructor.
   * 
   * @param {@link InitParams} params
   * @throws ConfigurationException
   */
  public GoogleDriveConnector(RepositoryService jcrService,
                              SessionProviderService sessionProviders,
                              InitParams params) throws ConfigurationException {
    super(jcrService, sessionProviders, params);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected CloudProvider createProvider() {
    StringBuilder redirectUrl = new StringBuilder();
    redirectUrl.append("http://");
    redirectUrl.append(getConnectorHost());
    redirectUrl.append("/portal/rest/clouddrive/connect/");
    redirectUrl.append(getProviderId());

    StringBuilder authUrl = new StringBuilder();
    authUrl.append("https://accounts.google.com/o/oauth2/auth?");
    authUrl.append("response_type=code&client_id=");
    authUrl.append(getClientId());
    authUrl.append("&approval_prompt=");
    authUrl.append(GoogleDriveAPI.APPOVAl_PROMT);
    authUrl.append("&scope=");
    authUrl.append(GoogleDriveAPI.SCOPES_STRING);
    authUrl.append("&access_type=");
    authUrl.append(GoogleDriveAPI.ACCESS_TYPE);
    authUrl.append("&state=");
    authUrl.append(GoogleDriveAPI.NO_STATE);
    authUrl.append("&redirect_uri=");
    authUrl.append(redirectUrl);

    return new GoogleProvider(getProviderId(), getProviderName(), authUrl.toString(), redirectUrl.toString(), jcrService);
  }

  private String getClientId() {
    return config.get(CONFIG_PROVIDER_CLIENT_ID);
  }

  private String getClientSecret() {
    return config.get(CONFIG_PROVIDER_CLIENT_SECRET);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected GoogleProvider getProvider() {
    return (GoogleProvider) super.getProvider();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GoogleUser authenticate(String code) throws CloudDriveException {
    if (code != null && code.length() > 0) {
      GoogleDriveAPI driveAPI = new API().auth(code).build();
      Userinfo userInfo = driveAPI.userInfo();
      GoogleUser user = new GoogleUser(userInfo.getId(),
                                       userInfo.getName(),
                                       userInfo.getEmail(),
                                       provider,
                                       driveAPI);
      return user;
    } else {
      throw new CloudDriveException("Access key should not be null or empty");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JCRLocalGoogleDrive createDrive(CloudUser user, Node driveNode) throws CloudDriveException,
                                                                           RepositoryException {
    if (user instanceof GoogleUser) {
      return new JCRLocalGoogleDrive((GoogleUser) user, driveNode, sessionProviders);
    } else {
      throw new CloudDriveException("Not Google user: " + user);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected CloudDrive loadDrive(Node driveNode) throws CloudDriveException, RepositoryException {
    return new JCRLocalGoogleDrive(new API(), getProvider(), driveNode, sessionProviders);
  }

}
