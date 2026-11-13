package com.example.student.dto.user;

import com.example.student.model.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import jakarta.validation.constraints.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    @Size(max = 254)
    private String email;
    private String password;
    @NotEmpty(message = "Выберите хотя бы одну роль")
    private Set<@NotNull Role> roles;
}
