package com.example.student.security;

import com.example.student.model.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService service=new JwtService();
    @Test void signedTokenContainsUsernameAndAllRoles() {
        var roles=Set.of(Role.ADMIN,Role.TEACHER);
        String token=service.generateToken("anna@example.com",roles);
        assertEquals(3,token.split("\\.").length);
        assertEquals("anna@example.com",service.extractUsername(token));
        assertEquals(roles,service.extractRoles(token));
    }
    @Test void validTokenMatchesUser() {
        String token=service.generateToken("anna@example.com",Set.of(Role.TEACHER));
        var user=User.withUsername("anna@example.com").password("hash").roles("TEACHER").build();
        assertTrue(service.validateToken(token,user));
    }
    @Test void tokenCannotAuthenticateAnotherUser() {
        String token=service.generateToken("anna@example.com",Set.of(Role.TEACHER));
        var other=User.withUsername("other@example.com").password("hash").roles("TEACHER").build();
        assertFalse(service.validateToken(token,other));
    }
    @Test void alteredSignatureIsRejected() {
        String token=service.generateToken("anna@example.com",Set.of(Role.TEACHER));
        int start=token.lastIndexOf('.')+1;
        char replacement=token.charAt(start)=='A'?'B':'A';
        String changed=token.substring(0,start)+replacement+token.substring(start+1);
        assertThrows(JwtException.class,()->service.extractUsername(changed));
    }
    @Test void malformedTokenIsRejected() {
        assertThrows(JwtException.class,()->service.extractUsername("not.a.jwt"));
    }
    @Test void tokenWithNoRolesRoundTripsAnEmptyRoleSet() {
        assertTrue(service.extractRoles(service.generateToken("anna@example.com",Set.of())).isEmpty());
    }
}
