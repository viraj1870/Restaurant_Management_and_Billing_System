package com.restaurant.rms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class MenuDtos {

    private MenuDtos() {}

    public record RestaurantDto(Long id, String name, String description, String cuisine, String address,
                                String phone, String email, String gstNumber, BigDecimal taxPercent,
                                boolean active, boolean acceptingOrders, Double averageRating, long ratingCount) {}

    public record RestaurantUpdateRequest(
            @NotBlank(message = "Restaurant name is required") @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Size(max = 100) String cuisine,
            @Size(max = 255) String address,
            @Pattern(regexp = "^$|^[0-9+\\- ]{6,15}$", message = "Invalid phone") String phone,
            @Email(message = "Invalid email") @Size(max = 150) String email,
            @Size(max = 20) String gstNumber,
            @NotNull @DecimalMin(value = "0.0", message = "Tax cannot be negative")
            @DecimalMax(value = "28.0", message = "Tax cannot exceed 28%") BigDecimal taxPercent,
            Boolean active,
            Boolean acceptingOrders) {}

    public record CategoryRequest(
            @NotBlank(message = "Category name is required") @Size(max = 100) String name,
            @Size(max = 255) String description,
            Integer displayOrder,
            Boolean active) {}

    public record CategoryDto(Long id, String name, String description, int displayOrder, boolean active, long itemCount) {}

    public record MenuItemRequest(
            @NotNull(message = "Category is required") Long categoryId,
            @NotBlank(message = "Item name is required") @Size(max = 150) String name,
            @Size(max = 500) String description,
            @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be greater than 0")
            @Digits(integer = 8, fraction = 2) BigDecimal price,
            Boolean vegetarian,
            Boolean available,
            @Size(max = 500) String imageUrl) {}

    public record MenuItemDto(Long id, Long categoryId, String categoryName, String name, String description,
                              BigDecimal price, boolean vegetarian, boolean available, String imageUrl) {}

    public record MenuCategoryGroup(Long categoryId, String categoryName, String description, List<MenuItemDto> items) {}

    public record MenuResponse(RestaurantDto restaurant, List<MenuCategoryGroup> categories) {}

    public record AvailabilityRequest(@NotNull Boolean available) {}
}
