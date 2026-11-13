package com.example.student.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import jakarta.validation.constraints.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class StudentRequest {
    @NotBlank(message = "Имя обязательно")
    @Size(max = 100)
    private String name;
    @NotBlank(message = "Фамилия обязательна")
    @Size(max = 100)
    private String surname;
    @JsonProperty("phone_number")
    @Size(max = 40)
    private String phoneNumber;
    @Email(message = "Некорректный email")
    @Size(max = 254)
    private String email;
}
