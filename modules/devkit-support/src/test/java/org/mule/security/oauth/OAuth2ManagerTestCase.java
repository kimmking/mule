/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.security.oauth;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.capability.ModuleCapability;
import org.mule.api.config.MuleProperties;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.store.ObjectStore;
import org.mule.common.security.oauth.OAuthState;
import org.mule.security.oauth.callback.RestoreAccessTokenCallback;
import org.mule.security.oauth.callback.SaveAccessTokenCallback;
import org.mule.security.oauth.util.HttpUtil;
import org.mule.security.oauth.util.OAuthResponseParser;
import org.mule.tck.size.SmallTest;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class OAuth2ManagerTestCase
{

    @Spy
    private TestOAuth2Manager manager = new TestOAuth2Manager();

    @Mock
    private ObjectStore<OAuthState> accessTokenObjectStore = null;

    private MuleContext muleContext = null;

    @Mock
    private KeyedPoolableObjectFactory objectFactory = null;

    @Mock(extraInterfaces = {Initialisable.class, Startable.class, Stoppable.class, Disposable.class,
        MuleContextAware.class})
    private OAuth2Adapter adapter = null;

    @Mock
    private HttpUtil httpUtil;

    @Mock
    private OAuthResponseParser oauthResponseParser;

    @Before
    public void setUp() throws Exception
    {
        this.muleContext = Mockito.mock(MuleContext.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(
            this.muleContext.getRegistry().lookupObject(
                Mockito.eq(MuleProperties.DEFAULT_USER_OBJECT_STORE_NAME))).thenReturn(
            this.accessTokenObjectStore);

        this.manager.setMuleContext(this.muleContext);
        this.manager.setHttpUtil(this.httpUtil);
        this.manager.setOauthResponseParser(this.oauthResponseParser);

        this.manager.initialise();
        this.manager.start();
    }

    @Test
    public void initialize() throws InitialisationException
    {
        Assert.assertSame(this.manager.getAccessTokenObjectStore(), this.accessTokenObjectStore);
        Mockito.verify(this.manager).createPoolFactory(this.manager, this.accessTokenObjectStore);
        Mockito.verify(this.manager).instantiateAdapter();
        Mockito.verify((Initialisable) this.adapter).initialise();
    }

    @Test
    public void start() throws MuleException
    {
        Mockito.verify((Startable) this.adapter).start();
    }

    @Test
    public void stop() throws MuleException
    {
        this.manager.stop();
        Mockito.verify((Stoppable) this.adapter).stop();
    }

    @Test
    public void dispose() throws MuleException
    {
        this.manager.dispose();
        Mockito.verify((Disposable) this.adapter).dispose();
    }

    @Test
    public void createAdapter() throws Exception
    {
        final String verifier = "verifier";
        
        Mockito.when(adapter.getAuthorizationUrl()).thenReturn("authorizationUrl");
        Mockito.when(adapter.getAccessTokenUrl()).thenReturn("accessTokenUrl");
        Mockito.when(adapter.getConsumerKey()).thenReturn("consumerKey");
        Mockito.when(adapter.getConsumerSecret()).thenReturn("consumerSecret");

        OAuth2Adapter adapter = this.manager.createAdapter(verifier);

        Assert.assertSame(adapter, this.adapter);

        Mockito.verify(adapter).setOauthVerifier(Mockito.eq(verifier));
        Mockito.verify(adapter).setAuthorizationUrl(Mockito.eq(this.adapter.getAuthorizationUrl()));
        Mockito.verify(adapter).setAccessTokenUrl(Mockito.eq(this.adapter.getAccessTokenUrl()));
        Mockito.verify(adapter).setConsumerKey(Mockito.eq(this.adapter.getConsumerKey()));
        Mockito.verify(adapter).setConsumerSecret(Mockito.eq(this.adapter.getConsumerSecret()));

        Mockito.verify(this.manager).setCustomProperties(adapter);
        Mockito.verify((MuleContextAware) adapter).setMuleContext(this.muleContext);
    }

    @Test
    public void buildAuthorizeUrl()
    {
        final Map<String, String> extraParameters = new LinkedHashMap<String, String>();
        extraParameters.put("extra1", "extra1");
        extraParameters.put("extra2", "extra2");
        final String authorizationUrl = "authorizationUrl";
        final String redirectUri = "redirectUri";

        Mockito.when(adapter.getAuthorizationUrl()).thenReturn(authorizationUrl);
        Mockito.when(adapter.getConsumerKey()).thenReturn("consumerKey");

        Assert.assertEquals(
            this.manager.buildAuthorizeUrl(extraParameters, null, redirectUri),
            "authorizationUrl?response_type=code&client_id=consumerKey&redirect_uri=redirectUri&extra1=extra1&extra2=extra2");

        Assert.assertEquals(this.manager.buildAuthorizeUrl(extraParameters, "custom", redirectUri),
            "custom?response_type=code&client_id=consumerKey&redirect_uri=redirectUri&extra1=extra1&extra2=extra2");
    }

    @Test
    public void restoreTokenWithCallback()
    {
        RestoreAccessTokenCallback callback = Mockito.mock(RestoreAccessTokenCallback.class);
        Mockito.when(this.adapter.getOauthRestoreAccessToken()).thenReturn(callback);
        final String accessToken = "accessToken";
        Mockito.when(callback.getAccessToken()).thenReturn(accessToken);

        Assert.assertTrue(this.manager.restoreAccessToken(this.adapter));

        Mockito.verify(callback).restoreAccessToken();
        Mockito.verify(adapter).setAccessToken(Mockito.eq(accessToken));
    }

    @Test
    public void restoreTokenWithoutCallback()
    {
        Assert.assertFalse(this.manager.restoreAccessToken(this.adapter));
    }

    @Test
    public void fetchAccessToken() throws Exception
    {
        final String accessTokenUrl = "accessTokenUrl";
        Mockito.when(adapter.getAccessTokenUrl()).thenReturn(accessTokenUrl);

        final String oauthVerifier = "oauthVerifier";
        final String consumerKey = "consumerKey";
        final String consumerSecret = "consumerSecret";
        final String redirectUri = "redirectUri";
        final String response = "response";
        final String requestBody = "code=oauthVerifier&client_id=consumerKey&client_secret=consumerSecret&grant_type=authorization_code&redirect_uri=redirectUri";
        final Pattern accessTokenPattern = Pattern.compile(".");
        final Pattern expirationPattern = Pattern.compile(".");
        final Pattern refreshTokenPattern = Pattern.compile(".");
        final String accessToken = "accessToken";
        final String refreshToken = "refreshToken";
        final Date expiration = new Date();

        SaveAccessTokenCallback saveCallback = Mockito.mock(SaveAccessTokenCallback.class);
        Mockito.when(this.adapter.getOauthSaveAccessToken()).thenReturn(saveCallback);

        Mockito.when(adapter.getOauthVerifier()).thenReturn(oauthVerifier);
        Mockito.when(adapter.getConsumerKey()).thenReturn(consumerKey);
        Mockito.when(adapter.getConsumerSecret()).thenReturn(consumerSecret);
        Mockito.when(adapter.getAccessCodePattern()).thenReturn(accessTokenPattern);
        Mockito.when(adapter.getRefreshTokenPattern()).thenReturn(refreshTokenPattern);
        Mockito.when(adapter.getExpirationTimePattern()).thenReturn(expirationPattern);
        Mockito.when(this.httpUtil.post(this.manager.getDefaultUnauthorizedConnector().getAccessTokenUrl(), requestBody)).thenReturn(response);
        Mockito.when(this.oauthResponseParser.extractAccessCode(accessTokenPattern, response)).thenReturn(
            accessToken);
        Mockito.when(this.oauthResponseParser.extractExpirationTime(expirationPattern, response)).thenReturn(
            expiration);
        Mockito.when(this.oauthResponseParser.extractRefreshToken(refreshTokenPattern, response)).thenReturn(
            refreshToken);

        this.manager.fetchAccessToken(adapter, redirectUri);

        Mockito.verify(this.httpUtil).post(this.manager.getDefaultUnauthorizedConnector().getAccessTokenUrl(), requestBody);
        Mockito.verify(this.oauthResponseParser).extractAccessCode(accessTokenPattern, response);
        Mockito.verify(this.adapter).setAccessToken(accessToken);
        Mockito.verify(this.adapter).setExpiration(expiration);
        Mockito.verify(this.adapter).setRefreshToken(refreshToken);
        Mockito.verify(this.adapter).getOauthSaveAccessToken();
        Mockito.verify(saveCallback).saveAccessToken(Mockito.anyString(), Mockito.anyString());

        Mockito.verify(adapter).postAuth();
        Mockito.verify(this.manager).fetchCallbackParameters(this.adapter, response);

    }

    @Test
    public void refreshAccessToken() throws Exception
    {
        final String accessTokenUrl = "accessTokenUrl";
        Mockito.when(this.adapter.getAccessTokenUrl()).thenReturn(accessTokenUrl);

        final String oauthVerifier = "oauthVerifier";
        final String consumerKey = "consumerKey";
        final String consumerSecret = "consumerSecret";
        final String response = "response";
        final String requestBody = "grant_type=refresh_token&client_id=consumerKey&client_secret=consumerSecret&refresh_token=refreshToken";
        final Pattern accessTokenPattern = Pattern.compile(".");
        final Pattern expirationPattern = Pattern.compile(".");
        final Pattern refreshTokenPattern = Pattern.compile(".");
        final String accessToken = "accessToken";
        final String refreshToken = "refreshToken";
        final Date expiration = new Date();

        SaveAccessTokenCallback saveCallback = Mockito.mock(SaveAccessTokenCallback.class);
        Mockito.when(this.adapter.getOauthSaveAccessToken()).thenReturn(saveCallback);

        Mockito.when(adapter.getOauthVerifier()).thenReturn(oauthVerifier);
        Mockito.when(adapter.getConsumerKey()).thenReturn(consumerKey);
        Mockito.when(adapter.getConsumerSecret()).thenReturn(consumerSecret);
        Mockito.when(adapter.getAccessCodePattern()).thenReturn(accessTokenPattern);
        Mockito.when(adapter.getRefreshTokenPattern()).thenReturn(refreshTokenPattern);
        Mockito.when(adapter.getExpirationTimePattern()).thenReturn(expirationPattern);
        Mockito.when(adapter.getRefreshToken()).thenReturn(refreshToken);
        Mockito.when(this.httpUtil.post(this.manager.getDefaultUnauthorizedConnector().getAccessTokenUrl(), requestBody)).thenReturn(response);
        Mockito.when(this.oauthResponseParser.extractAccessCode(accessTokenPattern, response)).thenReturn(
            accessToken);
        Mockito.when(this.oauthResponseParser.extractExpirationTime(expirationPattern, response)).thenReturn(
            expiration);
        Mockito.when(this.oauthResponseParser.extractRefreshToken(refreshTokenPattern, response)).thenReturn(
            refreshToken);

        this.manager.refreshAccessToken(adapter);

        Mockito.verify(this.adapter).setAccessToken(null);
        Mockito.verify(this.httpUtil).post(this.manager.getDefaultUnauthorizedConnector().getAccessTokenUrl(), requestBody);
        Mockito.verify(this.oauthResponseParser).extractAccessCode(accessTokenPattern, response);
        Mockito.verify(this.adapter).setAccessToken(accessToken);
        Mockito.verify(this.adapter).setExpiration(expiration);
        Mockito.verify(this.adapter).setRefreshToken(refreshToken);
        Mockito.verify(this.adapter).getOauthSaveAccessToken();
        Mockito.verify(saveCallback).saveAccessToken(Mockito.anyString(), Mockito.anyString());

        Mockito.verify(adapter).postAuth();
        Mockito.verify(this.manager).fetchCallbackParameters(this.adapter, response);

    }

    @Test(expected = IllegalStateException.class)
    public void refreshWithoutToken() throws Exception
    {
        this.manager.refreshAccessToken(this.adapter);
    }

    @Test
    public void capabilities()
    {
        for (ModuleCapability capability : ModuleCapability.values())
        {
            if (capability == ModuleCapability.LIFECYCLE_CAPABLE
                || capability == ModuleCapability.OAUTH2_CAPABLE
                || capability == ModuleCapability.OAUTH_ACCESS_TOKEN_MANAGEMENT_CAPABLE)
            {
                Assert.assertTrue(this.manager.isCapableOf(capability));
            }
            else
            {
                Assert.assertFalse(this.manager.isCapableOf(capability));
            }
        }
    }

    private class TestOAuth2Manager extends BaseOAuth2Manager<OAuth2Adapter>
    {

        private final transient Logger logger = LoggerFactory.getLogger(TestOAuth2Manager.class);

        @Override
        protected Logger getLogger()
        {
            return logger;
        }

        @Override
        protected KeyedPoolableObjectFactory createPoolFactory(OAuth2Manager<OAuth2Adapter> oauthManager,
                                                               ObjectStore<OAuthState> objectStore)
        {
            return objectFactory;
        }

        @Override
        protected void fetchCallbackParameters(OAuth2Adapter adapter, String response)
        {
        }

        @Override
        protected void setCustomProperties(OAuth2Adapter adapter)
        {
        }

        @Override
        protected OAuth2Adapter instantiateAdapter()
        {
            return adapter;
        }

    }
}
