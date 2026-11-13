package com.example.student.service;

import com.example.student.dto.StudentUpdateRequest;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.StudentMapperImpl;
import com.example.student.model.Student;
import com.example.student.repository.StudentRepository;
import com.example.student.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentServiceTest {
    StudentRepository repository;
    StudentServiceImpl service;
    Student student;
    @BeforeEach void setup() {
        repository=mock(StudentRepository.class);
        service=new StudentServiceImpl(repository,new StudentMapperImpl());
        student=new Student();student.setId(11L);student.setName("Анна");student.setSurname("Соколова");
        student.setEmail("old@example.com");student.setPhoneNumber("111");
    }
    void stored() {
        when(repository.findById(11L)).thenReturn(Optional.of(student));
        when(repository.save(any())).thenAnswer(i->i.getArgument(0));
    }
    @Test void phoneOnlyUpdateKeepsEmailAndName() {
        stored();service.update(11L,new StudentUpdateRequest("222",null));
        assertEquals("222",student.getPhoneNumber());assertEquals("old@example.com",student.getEmail());
        assertEquals("Анна",student.getName());assertEquals("Соколова",student.getSurname());
    }
    @Test void emailOnlyUpdateKeepsPhone() {
        stored();service.update(11L,new StudentUpdateRequest(null,"new@example.com"));
        assertEquals("111",student.getPhoneNumber());assertEquals("new@example.com",student.getEmail());
    }
    @Test void omittedContactsLeaveDataUnchanged() {
        stored();service.update(11L,new StudentUpdateRequest(null,null));
        assertEquals("111",student.getPhoneNumber());assertEquals("old@example.com",student.getEmail());
    }
    @Test void emptyContactsExplicitlyClearTheFields() {
        stored();service.update(11L,new StudentUpdateRequest("",""));
        assertEquals("",student.getPhoneNumber());assertEquals("",student.getEmail());
    }
    @Test void unknownStudentCannotBeUpdated() {
        assertThrows(EntityNotFoundException.class,()->service.update(999L,new StudentUpdateRequest("222",null)));
        verify(repository,never()).save(any());
    }
    @Test void fullCardIncludesContactData() {
        when(repository.findById(11L)).thenReturn(Optional.of(student));
        var result=service.findById(11L);
        assertEquals(11L,result.getId());assertEquals("Анна",result.getName());
        assertEquals("Соколова",result.getSurname());assertEquals("111",result.getPhoneNumber());
        assertEquals("old@example.com",result.getEmail());
    }
    @Test void emptyDirectoryHasNoRowsAndZeroCount() {
        when(repository.findAll()).thenReturn(List.of());
        var result=service.findAll();assertTrue(result.getResponse().isEmpty());assertEquals(0,result.getTotalCount());
    }
}
