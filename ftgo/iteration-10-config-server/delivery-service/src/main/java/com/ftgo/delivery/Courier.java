package com.ftgo.delivery;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Courier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String phone;
    private boolean available;
}
