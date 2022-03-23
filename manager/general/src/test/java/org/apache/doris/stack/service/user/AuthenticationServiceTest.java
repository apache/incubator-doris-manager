// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.stack.service.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import org.apache.doris.stack.constant.PropertyDefine;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import org.apache.doris.stack.model.ldap.LdapConnectionInfo;
import org.apache.doris.stack.model.ldap.LdapUserInfo;
import org.apache.doris.stack.model.ldap.LdapUserInfoReq;
import org.apache.doris.stack.model.request.user.PasswordResetReq;
import org.apache.doris.stack.model.request.user.UserLoginReq;
import org.apache.doris.stack.model.response.config.LdapSettingResp;
import org.apache.doris.stack.util.UuidUtil;
import org.apache.doris.stack.component.IdaasComponent;
import org.apache.doris.stack.component.LdapComponent;
import org.apache.doris.stack.component.MailComponent;
import org.apache.doris.stack.component.SettingComponent;
import org.apache.doris.stack.component.UserActivityComponent;
import org.apache.doris.stack.connector.LdapClient;
import org.apache.doris.stack.dao.CoreSessionRepository;
import org.apache.doris.stack.dao.CoreUserRepository;
import org.apache.doris.stack.dao.LoginHistoryRepository;
import org.apache.doris.stack.dao.PermissionsGroupMembershipRepository;
import org.apache.doris.stack.entity.CoreSessionEntity;
import org.apache.doris.stack.entity.CoreUserEntity;
import org.apache.doris.stack.entity.PermissionsGroupMembershipEntity;
import org.apache.doris.stack.entity.SettingEntity;
import org.apache.doris.stack.exception.AuthorizationException;
import org.apache.doris.stack.exception.LdapConnectionException;
import org.apache.doris.stack.exception.RequestFieldNullException;
import org.apache.doris.stack.exception.ResetPasswordTokenException;
import org.apache.doris.stack.exception.UserFailedLoginTooManyException;
import org.apache.doris.stack.exception.UserLoginException;
import org.apache.doris.stack.exception.UserLoginTooManyException;
import org.apache.doris.stack.service.UtilService;
import com.unboundid.ldap.sdk.LDAPConnection;

import lombok.extern.slf4j.Slf4j;
import nl.bitwalker.useragentutils.UserAgent;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(JUnit4.class)
@Slf4j
public class AuthenticationServiceTest {

    private static final String COOKIE_NAME = "studio.SESSION";

    @InjectMocks
    AuthenticationService authenticationService;

    @Mock
    private CoreUserRepository userRepository;

    @Mock
    private CoreSessionRepository sessionRepository;

    @Mock
    private UtilService utilService;

    @Mock
    private MailComponent mailComponent;

    @Mock
    private Environment environment;

    @Mock
    private SettingComponent settingComponent;

    @Mock
    private LdapComponent ldapComponent;

    @Mock
    private IdaasComponent idaasComponent;

    @Mock
    private LdapClient ldapClient;

    @Mock
    private PermissionsGroupMembershipRepository permissionsGroupMembership;

    @Mock
    private UserActivityComponent activityComponent;

    @Mock
    private HttpServletRequest request;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test email password login
     */
    @Test
    @Transactional
    public void testLogin() throws Exception {
        log.debug("test login.");
        String passwd = "ewe232";
        String userName = "cai@test.com";
        int userId = 1;
        when(environment.getProperty(PropertyDefine.LOGIN_DELAY_TIME_PROPERTY)).thenReturn("300000");
        when(environment.getProperty(PropertyDefine.MAX_LOGIN_FAILED_TIMES_PROPERTY)).thenReturn("10");
        when(environment.getProperty(PropertyDefine.MAX_LOGIN_TIMES_IN_FIVE_MINUTES_PROPERTY)).thenReturn("500");
        when(environment.getProperty(PropertyDefine.MAX_LOGIN_TIMES_PROPERTY)).thenReturn("2000");
        UserLoginReq loginReq = new UserLoginReq();
        // mock user agent
        String requestUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like "
                + "Gecko) Chrome/91.0.4472.114 Safari/537.36.";
        when(request.getHeader("User-Agent")).thenReturn(requestUserAgent);
        UserAgent userAgent = UserAgent.parseUserAgentString(requestUserAgent);
        Assert.assertEquals(50990858, userAgent.getId());
        Assert.assertEquals("Chrome 9", userAgent.getBrowser().getName());

        // request exception
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(RequestFieldNullException.MESSAGE, e.getMessage());
        }

        ConcurrentHashMap<Integer, Long> loginAllowMap = AuthenticationService.loginNotAllowMap;
        ConcurrentHashMap<Integer, List<Long>> failedLoginMap = AuthenticationService.failedLoginMap;
        List<Long> loginAttempts = new ArrayList<>();
        loginAttempts.add(System.currentTimeMillis());
        failedLoginMap.put(0, loginAttempts);

        // mock the number of historical logins
        when(loginHistoryRepository.getLoginCountByUserId(userId)).thenReturn(2);
        // mock the historical login times of the device
        when(loginHistoryRepository.getLoginCountByUserIdAndDeviceId(userId, "50990858")).thenReturn(0);

        // Ordinary user login
        loginReq.setUsername(userName);
        loginReq.setPassword(passwd);
        CoreUserEntity userEntity = new CoreUserEntity();
        userEntity.setId(userId);
        // Enable LDAP
        when(ldapComponent.enabled()).thenReturn(true);
        // user not exist
        when(userRepository.getByEmailAndLdapAuth(userName, true)).thenReturn(Lists.emptyList());
        LdapSettingResp resp = new LdapSettingResp();
        resp.setLdapHost("127.0.0.1");
        resp.setLdapPort(389);
        resp.setLdapBindDn("user");
        resp.setLdapPassword("passwd");
        when(ldapComponent.readLdapConfig()).thenReturn(resp);

        LDAPConnection ldapConnection = new LDAPConnection();
        when(ldapClient.getConnection(any(LdapConnectionInfo.class))).thenReturn(null);
        failedLoginMap.put(userId, Lists.newArrayList(System.currentTimeMillis()));
        when(idaasComponent.enabled()).thenReturn(false);

        // ldap connect fail
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(LdapConnectionException.MESSAGE, e.getMessage());
        }
        // mock ldap connection
        when(ldapClient.getConnection(any(LdapConnectionInfo.class))).thenReturn(ldapConnection);
        resp.setLdapUserBase(Lists.newArrayList("userbase"));
        resp.setLdapAttributeEmail("mail");

        LdapUserInfo userInfo = new LdapUserInfo();
        userInfo.setFirstName("firstname");
        userInfo.setLastName("lastname");
        userInfo.setAuth(true);
        when(ldapClient.authenticate(any(LDAPConnection.class), any(LdapUserInfoReq.class))).thenReturn(null);
        // ldap Authentication failed
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(UserLoginException.MESSAGE, e.getMessage());
        }
        // ldap User first login
        when(ldapClient.authenticate(any(LDAPConnection.class), any(LdapUserInfoReq.class))).thenReturn(userInfo);
        SettingEntity defaultGroup = new SettingEntity();
        defaultGroup.setValue("0");
        // mock default group
        when(settingComponent.readSetting("default-group-id")).thenReturn(defaultGroup);

        SettingEntity authType = new SettingEntity();
        authType.setValue("ldap");
        // mock auth type
        when(settingComponent.readSetting("auth_type")).thenReturn(authType);
        PermissionsGroupMembershipEntity permissionsGroupMembershipEntity = new PermissionsGroupMembershipEntity();
        permissionsGroupMembershipEntity.setGroupId(Integer.parseInt(defaultGroup.getValue()));
        // mock user
        when(userRepository.save(any(CoreUserEntity.class))).thenReturn(userEntity);
        permissionsGroupMembershipEntity.setUserId(userId);
        // save user
        permissionsGroupMembership.save(permissionsGroupMembershipEntity);

        // mock session times
        when(sessionRepository.getSessionCountBeforeByUserId(anyInt(), any())).thenReturn(50);
        when(sessionRepository.getSessionCountByUserId(0)).thenReturn(10);
        authenticationService.login(loginReq, request);

        // ldap user is not logged in for the first time
        when(userRepository.getByEmailAndLdapAuth(userName, true)).thenReturn(Lists.newArrayList(userEntity));
        authenticationService.login(loginReq, request);

        when(ldapComponent.enabled()).thenReturn(false);
        // Not enable ldap
        when(userRepository.getByEmailAndLdapAuth(userName, true)).thenReturn(Lists.emptyList());
        // user not exsit
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(UserLoginException.MESSAGE, e.getMessage());
        }
        // mock user
        failedLoginMap.put(userId, Lists.newArrayList(System.currentTimeMillis()));
        when(userRepository.getByEmailAndLdapAuth(userName, false)).thenReturn(Lists.newArrayList(userEntity));

        authenticationService.login(loginReq, request);

        failedLoginMap.put(userId, Lists.newArrayList(System.currentTimeMillis()));
        // The number of ordinary users online at the same time exceeds the threshold

        when(sessionRepository.getSessionCountBeforeByUserId(any(Integer.class), any())).thenReturn(550);
        when(sessionRepository.getSessionCountByUserId(userId)).thenReturn(5550);
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(UserLoginTooManyException.MESSAGE, e.getMessage());
        }

        // The number of login failures is greater than the threshold
        when(sessionRepository.getSessionCountBeforeByUserId(any(Integer.class), any())).thenReturn(50);
        when(sessionRepository.getSessionCountByUserId(userId)).thenReturn(50);
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(UserLoginTooManyException.MESSAGE, e.getMessage());
        }

        AuthenticationService.loginNotAllowSessionMap.remove(userId);
        List<Long> failedLoginList = AuthenticationService.failedLoginMap.get(userId);
        for (int i = 0; i < 11; i++) {
            failedLoginList.add(System.currentTimeMillis());
        }
        failedLoginList.add(System.currentTimeMillis() - 1000 * 60 * 20);
        failedLoginMap.put(userId, failedLoginList);
        try {
            authenticationService.login(loginReq, request);
        } catch (Exception e) {
            Assert.assertEquals(UserFailedLoginTooManyException.MESSAGE, e.getMessage());
        }

    }

    @Test
    public void testLogout() throws Exception {
        log.debug("test logout.");
        String sessionId = "fsfs";
        Cookie cookie = new Cookie(COOKIE_NAME, null);
        Connector connector = new Connector();
        Request request = new Request(connector);

        // request no have cookie
        HttpServletResponse response = new MockHttpServletResponse();
        try {
            authenticationService.logout(request, response);
        } catch (Exception e) {
            Assert.assertEquals(AuthorizationException.MESSAGE, e.getMessage());
        }
        request.addCookie(cookie);
        // cookie no have value
        try {
            authenticationService.logout(request, response);
        } catch (Exception e) {
            Assert.assertEquals(AuthorizationException.MESSAGE, e.getMessage());
        }
        cookie = new Cookie(COOKIE_NAME, sessionId);
        request.addCookie(cookie);
//        when(superUserRepository.getByValue(sessionId)).thenReturn(null);
        CoreSessionEntity sessionEntity = new CoreSessionEntity();
        sessionEntity.setId(sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        // cookie not exist
        try {
            authenticationService.logout(request, response);
        } catch (Exception e) {
            Assert.assertEquals(AuthorizationException.MESSAGE, e.getMessage());
        }
        // mock session
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionEntity));
        sessionEntity.setCreatedAt(new Timestamp(1592991029 * 1000L));
        sessionEntity.setUserId(1);
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("200000");
        // cookie expired
        try {
            authenticationService.logout(request, response);
        } catch (Exception e) {
            Assert.assertEquals(AuthorizationException.MESSAGE, e.getMessage());
        }
        // cookie not expired
        sessionEntity.setCreatedAt(new Timestamp(System.currentTimeMillis() * 1000L));
        authenticationService.logout(request, response);
   }

    @Test
    public void testClearResponseCookie() {

        HttpServletResponse httpServletResponse = new MockHttpServletResponse();
        String sessionId = "ewewfs213a33";
        authenticationService.clearResponseCookie(httpServletResponse, sessionId);
    }

    @Test
    public void testVerifyToken() throws Exception {

        String token = "123456";
        String encodePwd = BCrypt.hashpw(token, BCrypt.gensalt());
        CoreUserEntity userEntity = new CoreUserEntity();
        userEntity.setResetToken(encodePwd);
        userEntity.setResetTriggered(0L);
        Assert.assertFalse(authenticationService.verifyToken(token, userEntity));
        userEntity.setResetTriggered(System.currentTimeMillis() - 1L);
        Assert.assertTrue(authenticationService.verifyToken(token, userEntity));
    }

    @Test
    public void testClearExpiredCookie() {
        log.debug("test clear expired cookie.");
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("q");
        // Failed to get the maximum validity period of session
        try {
            authenticationService.clearExpiredCookieAndLoginHistory();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("1000");
        authenticationService.clearExpiredCookieAndLoginHistory();
    }

    @Test
    public void testSetResponseCookie() {
        log.debug("test set response cookie.");
        String sessionId = "323sds3saa";
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("2000");
        HttpServletResponse response = new MockHttpServletResponse();
        authenticationService.setResponseCookie(response, sessionId);

    }

    @Test
    public void testCheckAllUserAuthWithCookie() throws Exception {
        log.debug("test check all user with cookie");
        String sessionId = "fsfs";
        HttpServletResponse response = new MockHttpServletResponse();
        Connector connector = new Connector();
        Request request = new Request(connector);

        Cookie cookie = new Cookie(COOKIE_NAME, sessionId);
        request.addCookie(cookie);

        CoreSessionEntity sessionEntity = new CoreSessionEntity();
        sessionEntity.setId(sessionId);
        sessionEntity.setCreatedAt(new Timestamp(1592991029 * 1000L));
        sessionEntity.setUserId(1);
        // mock session
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionEntity));
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("200000");

        // cookie not expired
        sessionEntity.setCreatedAt(new Timestamp(System.currentTimeMillis() * 1000L));
        // mock user
        Assert.assertEquals(1, authenticationService.checkUserAuthWithCookie(request, response));

        // Super administrator
        sessionEntity.setUserId(0);
        // mock session
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionEntity));
        Assert.assertEquals(0, authenticationService.checkUserAuthWithCookie(request, response));
    }

    @Test
    public void testResetTokenValid() throws Exception {
        log.debug("test reset token valid.");
        String token = "1_4343ee_1";
        int userId = 1;
        when(ldapComponent.enabled()).thenReturn(true);
        // ldap user can't be reseted password
        try {
            authenticationService.resetTokenValid(token);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        when(ldapComponent.enabled()).thenReturn(false);
        // Token format error
        try {
            authenticationService.resetTokenValid(token);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        token = "r_frgsdgs";
        // Token format error, unable to get user ID
        try {
            authenticationService.resetTokenValid(token);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        token = "1_rff434d";
        CoreUserEntity userEntity = new CoreUserEntity();
        userEntity.setId(userId);
        // mock user
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        String encodePwd = BCrypt.hashpw("rff434d", BCrypt.gensalt());
        userEntity.setResetToken(encodePwd);
        userEntity.setResetTriggered(System.currentTimeMillis() - 1L);
        Assert.assertTrue(authenticationService.resetTokenValid(token));

    }

    @Test
    @Transactional
    public void testResetPassword() throws Exception {
        log.debug("test reset password.");
        PasswordResetReq resetReq = new PasswordResetReq();
        when(ldapComponent.enabled()).thenReturn(true);
        // ldap user can't be reseted password
        try {
            authenticationService.resetPassword(resetReq);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        when(ldapComponent.enabled()).thenReturn(false);

        String password = "323";
        String token = "1_dsds";
        resetReq.setPassword(password);
        resetReq.setToken(token);
        CoreUserEntity userEntity = new CoreUserEntity();
        userEntity.setId(1);
        String encodePwd = BCrypt.hashpw("dsds", BCrypt.gensalt());
        userEntity.setResetToken(encodePwd);
        // mock user
        when(userRepository.findById(1)).thenReturn(Optional.of(userEntity));
        // Null pointer exception. The user has no resettriggered password reset time point
        try {
            authenticationService.resetPassword(resetReq);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        // token Not invalid
        userEntity.setResetTriggered(System.currentTimeMillis() - 1L);
        authenticationService.resetPassword(resetReq);

    }

    @Test
    @Transactional
    public void testForgetPassword() throws Exception {
        log.debug("test forget password.");
<<<<<<< HEAD
<<<<<<< HEAD
        String email = "{\"email\":\"sss@test.com\"}";
=======
        String email = "{\"email\":\"sss@xxx.com\"}";
>>>>>>> dee0d0c (delete mail info)
=======
        String email = "{\"email\":\"sss@xxx.com\"}";
>>>>>>> 8b3352b8fb022789dd2724003ee070cecd722452
        String hostName = "ss";
        when(ldapComponent.enabled()).thenReturn(true);
        // ldap user can't be reseted password
        try {
            authenticationService.forgetPassword(email, hostName);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        when(ldapComponent.enabled()).thenReturn(false);
        CoreUserEntity userEntity = new CoreUserEntity();
        userEntity.setEmail(email);
        userEntity.setId(1);
        when(userRepository.getByEmail(email)).thenReturn(Lists.emptyList());
        // user not exist
        try {
            authenticationService.forgetPassword(email, hostName);
        } catch (Exception e) {
            Assert.assertEquals(ResetPasswordTokenException.MESSAGE, e.getMessage());
        }
        // mock user
<<<<<<< HEAD
<<<<<<< HEAD
        when(userRepository.getByEmail("sss@test.com")).thenReturn(Lists.newArrayList(userEntity));
=======
        when(userRepository.getByEmail("sss@xxx.com")).thenReturn(Lists.newArrayList(userEntity));
>>>>>>> dee0d0c (delete mail info)
=======
        when(userRepository.getByEmail("sss@xxx.com")).thenReturn(Lists.newArrayList(userEntity));
>>>>>>> 8b3352b8fb022789dd2724003ee070cecd722452
        String resetTokenStr = UuidUtil.newUuid();
        // mock reset password token
        when(utilService.resetUserToken(userEntity, false)).thenReturn(resetTokenStr);
        String resetUrl = "/auth/reset_password/1_" + resetTokenStr;
        // mock reset password url
        when(utilService.getResetPasswordUrl(any(Integer.class), any(String.class))).thenReturn(resetUrl);
        authenticationService.forgetPassword(email, hostName);

    }

    @Test
    public void testCheckUserAuthWithCookie() throws Exception {
        log.debug("test check user auth with cookie.");
        String sessionId = "fsfs";
        HttpServletResponse response = new MockHttpServletResponse();
        Connector connector = new Connector();
        Request request = new Request(connector);

        Cookie cookie = new Cookie(COOKIE_NAME, sessionId);
        request.addCookie(cookie);

        CoreSessionEntity sessionEntity = new CoreSessionEntity();
        sessionEntity.setUserId(1);
        sessionEntity.setId(sessionId);
        // mock session
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionEntity));
        when(environment.getProperty(PropertyDefine.MAX_SESSION_AGE_PROPERTY)).thenReturn("2000");
        // cookie not expired
        sessionEntity.setCreatedAt(new Timestamp(System.currentTimeMillis() * 1000L));
        Assert.assertEquals(1, authenticationService.checkUserAuthWithCookie(request, response));
    }

}
