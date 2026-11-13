package com.example.student.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtFilterTest {
    UserDetailsService users;
    JwtService jwt;
    JwtFilter filter;
    FilterChain chain;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    UserDetails user;
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        users=mock(UserDetailsService.class);jwt=mock(JwtService.class);chain=mock(FilterChain.class);
        filter=new JwtFilter(users,jwt);
        request=new MockHttpServletRequest("GET","/api/v1/student");
        response=new MockHttpServletResponse();
        user=User.withUsername("anna@example.com").password("hash").roles("TEACHER").build();
    }
    @AfterEach void clearContext() {SecurityContextHolder.clearContext();}
    void bearer() {request.addHeader("Authorization","Bearer signed-token");}
    @Test void missingTokenLeavesAuthenticationToSecurityChain() throws Exception {
        filter.doFilter(request,response,chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request,response);verifyNoInteractions(jwt,users);
    }
    @Test void anotherAuthorizationSchemeIsNotTreatedAsJwt() throws Exception {
        request.addHeader("Authorization","Basic credentials");
        filter.doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(jwt,users);
    }
    @Test void validTokenAuthenticatesWithCurrentDatabaseRoles() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenReturn(user.getUsername());
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        when(jwt.validateToken("signed-token",user)).thenReturn(true);
        filter.doFilter(request,response,chain);
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);assertTrue(authentication.isAuthenticated());
        assertEquals(user.getUsername(),authentication.getName());
        assertEquals(java.util.Set.copyOf(user.getAuthorities()),java.util.Set.copyOf(authentication.getAuthorities()));
        verify(chain).doFilter(request,response);
    }
    @Test void failedValidationDoesNotAuthenticate() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenReturn(user.getUsername());
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        when(jwt.validateToken("signed-token",user)).thenReturn(false);
        filter.doFilter(request,response,chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request,response);
    }
    @Test void existingAuthenticationIsNotOverwritten() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenReturn("other@example.com");
        var original=new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(original);
        filter.doFilter(request,response,chain);
        assertSame(original,SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);verify(jwt,never()).validateToken(anyString(),any());
        verify(chain).doFilter(request,response);
    }
    @Test void tokenWithoutSubjectDoesNotAuthenticate() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenReturn(null);
        filter.doFilter(request,response,chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);verify(chain).doFilter(request,response);
    }
    @Test void expiredJwtReturnsUnauthorizedAndDoesNotLeakException() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenThrow(new ExpiredJwtException(null,null,"internal expiration details"));
        filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus());
        assertEquals("application/json;charset=UTF-8",response.getContentType());
        assertTrue(response.getContentAsString().contains("Токен недействителен"));
        assertFalse(response.getContentAsString().contains("internal expiration details"));
        verifyNoInteractions(chain,users);
    }
    @Test void deletedUserCannotUseAnOldToken() throws Exception {
        bearer();when(jwt.extractUsername("signed-token")).thenReturn(user.getUsername());
        when(users.loadUserByUsername(user.getUsername())).thenThrow(new UsernameNotFoundException("deleted"));
        filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }
    @Test void nonApiParsingErrorIsPropagated() {
        request=new MockHttpServletRequest("GET","/other");bearer();
        when(jwt.extractUsername("signed-token")).thenThrow(new MalformedJwtException("bad token"));
        assertThrows(MalformedJwtException.class,()->filter.doFilter(request,response,chain));
        verifyNoInteractions(chain);
    }
}
