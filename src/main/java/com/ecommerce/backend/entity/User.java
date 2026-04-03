package com.ecommerce.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;





@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User {


    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    @Enumerated(EnumType.STRING)
    private Role role;
    private String name;
    private String email;
    private String password;
}