package com.example.student.configuration;

import com.example.student.model.User;
import com.example.student.repository.StudentRepository;
import com.example.student.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppInitializerTest {
    @Test void firstRunSeedsThreeAccountsWithEncodedPasswordsAndFiveStudents() {
        var users=mock(UserRepository.class);var students=mock(StudentRepository.class);
        var encoder=mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenAnswer(i->"hash:"+i.getArgument(0));
        new AppInitializer(users,encoder,students).init();
        var saved=ArgumentCaptor.forClass(User.class);
        verify(users,times(3)).save(saved.capture());
        assertEquals(3,saved.getAllValues().stream().map(User::getEmail).distinct().count());
        assertTrue(saved.getAllValues().stream().allMatch(u->u.getPassword().startsWith("hash:")&&!u.getRoles().isEmpty()));
        verify(students,times(5)).save(argThat(s->s.getName()!=null&&s.getSurname()!=null));
    }
    @Test void subsequentRunDoesNotOverwriteAccountsOrStudentData() {
        var users=mock(UserRepository.class);var students=mock(StudentRepository.class);
        var encoder=mock(PasswordEncoder.class);
        when(users.existsByEmail(anyString())).thenReturn(true);
        when(students.count()).thenReturn(5L);
        new AppInitializer(users,encoder,students).init();
        verify(users,never()).save(any());verify(students,never()).save(any());
        verifyNoInteractions(encoder);
    }
}
