package com.example.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StudentUpdateRequest {
    @Size(max = 40)
    private String phoneNumber;
    @Email(message = "Некорректный email")
    @Size(max = 254)
    private String email;
}
