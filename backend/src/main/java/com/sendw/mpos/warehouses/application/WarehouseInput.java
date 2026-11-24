package com.sendw.mpos.warehouses.application;
import jakarta.validation.constraints.*;
public record WarehouseInput(
        @NotBlank @Size(max=255) String name,
        @NotBlank @Pattern(regexp="[0-9+() -]{1,40}") String phone,
        @NotBlank @Size(max=100) String country,
        @NotBlank @Size(max=100) String city,
        @Email @Size(max=254) String email,
        @Size(max=30) String zip_code) {}
