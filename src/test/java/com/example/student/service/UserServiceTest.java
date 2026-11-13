package com.example.student.service;

import com.example.student.dto.user.UserRequest;
import com.example.student.exception.EmailAlreadyExistsException;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.UserMapperImpl;
import com.example.student.model.Role;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    UserRepository repository;
    PasswordEncoder encoder;
    UserServiceImpl service;
    @BeforeEach void setup() {
        repository = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new UserServiceImpl(repository, new UserMapperImpl(), encoder);
    }
    private UserRequest request(String password) {
        return new UserRequest("new@example.com", password, Set.of(Role.TEACHER));
    }
    private User existing() {
        return new User(7L, "old@example.com", "old-hash", Set.of(Role.ADMIN));
    }
    private void allowSave() {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) u.setId(7L);
            return u;
        });
    }
    @Test void registrationEncodesPasswordAndRetainsRoles() {
        allowSave();
        when(encoder.encode("secret123")).thenReturn("encoded-password");
        assertTrue(service.register(request("secret123")));
        verify(repository).save(argThat(u -> u.getEmail().equals("new@example.com")
                && u.getPassword().equals("encoded-password") && u.getRoles().equals(Set.of(Role.TEACHER))));
    }
    @Test void duplicateRegistrationDoesNotWriteOrEncode() {
        when(repository.existsByEmail("new@example.com")).thenReturn(true);
        assertFalse(service.register(request("secret123")));
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void registrationWithoutEmailDoesNotReachDatabase() {
        assertFalse(service.register(new UserRequest(null,"secret123",Set.of(Role.TEACHER))));
        verifyNoInteractions(repository,encoder);
    }
    @Test void emailLookupReturnsUser() {
        User user = existing();
        when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertSame(user,service.findByEmail(user.getEmail()));
    }
    @Test void unknownEmailProducesDomainError() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,()->service.findByEmail("missing@example.com"));
    }
    @Test void existenceCheckUsesRequestedEmail() {
        when(repository.existsByEmail("present@example.com")).thenReturn(true);
        assertTrue(service.existsByEmail("present@example.com"));
        assertFalse(service.existsByEmail("missing@example.com"));
    }
    @Test void listContainsPublicFieldsAndCorrectCount() {
        when(repository.findAll()).thenReturn(List.of(existing()));
        var result=service.findAll();
        assertEquals(1,result.getTotalCount());
        assertEquals(7L,result.getResponses().getFirst().getId());
        assertEquals("old@example.com",result.getResponses().getFirst().getEmail());
        assertEquals(Set.of(Role.ADMIN),result.getResponses().getFirst().getRoles());
    }
    @Test void emptyListHasZeroCount() {
        when(repository.findAll()).thenReturn(List.of());
        var result=service.findAll();
        assertEquals(0,result.getTotalCount());
        assertTrue(result.getResponses().isEmpty());
    }
    @Test void creationStoresHashInsteadOfRawPassword() {
        allowSave();
        when(encoder.encode("secret123")).thenReturn("new-hash");
        var result=service.save(request("secret123"));
        assertEquals("new@example.com",result.getEmail());
        assertEquals(Set.of(Role.TEACHER),result.getRoles());
        verify(repository).save(argThat(u -> u.getPassword().equals("new-hash")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   ","12345"})
    void creationRejectsMissingOrShortPassword(String password) {
        assertThrows(IllegalArgumentException.class,()->service.save(request(password)));
        verifyNoInteractions(repository,encoder);
    }
    @Test void creationRejectsTooLongPassword() {
        assertThrows(IllegalArgumentException.class,()->service.save(request("x".repeat(73))));
        verifyNoInteractions(repository,encoder);
    }
    @Test void duplicateCreationDoesNotSave() {
        when(repository.existsByEmail("new@example.com")).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class,()->service.save(request("secret123")));
        verify(repository,never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void updateUsesIdAndChangesEmailRolesAndPassword() {
        User user=existing();
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.encode("secret123")).thenReturn("changed-hash");
        allowSave();
        var result=service.updateUser(7L,request("secret123"));
        assertEquals(7L,result.getId());
        assertEquals("new@example.com",result.getEmail());
        assertEquals(Set.of(Role.TEACHER),result.getRoles());
        assertEquals("changed-hash",user.getPassword());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void updateWithoutNewPasswordKeepsExistingHash(String password) {
        User user=existing();
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.findByEmail("old@example.com")).thenReturn(Optional.of(user));
        allowSave();
        var result=service.updateUser(7L,new UserRequest("old@example.com",password,Set.of(Role.TEACHER)));
        assertEquals("old-hash",user.getPassword());
        assertEquals(Set.of(Role.TEACHER),result.getRoles());
        verifyNoInteractions(encoder);
    }
    @ParameterizedTest @ValueSource(ints={5,73})
    void updateRejectsInvalidNewPasswordLength(int length) {
        when(repository.findById(7L)).thenReturn(Optional.of(existing()));
        assertThrows(IllegalArgumentException.class,()->service.updateUser(7L,request("x".repeat(length))));
        verify(repository,never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void updateCannotTakeEmailOwnedBySomeoneElse() {
        when(repository.findById(7L)).thenReturn(Optional.of(existing()));
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.of(new User(9L,"new@example.com","hash",Set.of(Role.ADMIN))));
        assertThrows(EmailAlreadyExistsException.class,()->service.updateUser(7L,request("secret123")));
        verify(repository,never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void unknownUserCannotBeUpdated() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,()->service.updateUser(999L,request("secret123")));
        verify(repository,never()).save(any());
    }
}
