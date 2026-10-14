package com.restaurant.rms.service;

import com.restaurant.rms.dto.MenuDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.Category;
import com.restaurant.rms.model.MenuItem;
import com.restaurant.rms.model.Restaurant;
import com.restaurant.rms.repository.CategoryRepository;
import com.restaurant.rms.repository.FeedbackRepository;
import com.restaurant.rms.repository.MenuItemRepository;
import com.restaurant.rms.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restaurant profile, categories and menu items. */
@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final FeedbackRepository feedbackRepository;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;

    public RestaurantService(RestaurantRepository restaurantRepository, CategoryRepository categoryRepository,
                             MenuItemRepository menuItemRepository, FeedbackRepository feedbackRepository,
                             CurrentUserService currentUser, DtoMapper mapper) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.feedbackRepository = feedbackRepository;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ---------- Public ----------

    public List<RestaurantDto> listActive() {
        return restaurantRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toDto).toList();
    }

    public RestaurantDto getPublic(Long id) {
        return toDto(activeRestaurant(id));
    }

    /** Menu grouped by category. Customers only see active categories and available items. */
    public MenuResponse publicMenu(Long restaurantId) {
        Restaurant r = activeRestaurant(restaurantId);
        return buildMenu(r, false);
    }

    public Restaurant activeRestaurant(Long id) {
        return restaurantRepository.findById(id).filter(Restaurant::isActive)
                .orElseThrow(() -> ApiException.notFound("Restaurant not found"));
    }

    public RestaurantDto toDto(Restaurant r) {
        var rating = feedbackRepository.ratingSummary(r.getId());
        Double avg = rating == null || rating.getAverage() == null ? null
                : java.math.BigDecimal.valueOf(rating.getAverage()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        long count = rating == null || rating.getCount() == null ? 0 : rating.getCount();
        return mapper.restaurant(r, avg, count);
    }

    // ---------- Merchant: profile ----------

    public RestaurantDto myRestaurant() {
        return toDto(currentUser.merchantRestaurant());
    }

    @Transactional
    public RestaurantDto updateMyRestaurant(RestaurantUpdateRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        r.setName(req.name().trim());
        r.setDescription(blankToNull(req.description()));
        r.setCuisine(blankToNull(req.cuisine()));
        r.setAddress(blankToNull(req.address()));
        r.setPhone(blankToNull(req.phone()));
        r.setEmail(blankToNull(req.email()));
        r.setGstNumber(blankToNull(req.gstNumber()));
        r.setTaxPercent(req.taxPercent());
        if (req.active() != null) r.setActive(req.active());
        if (req.acceptingOrders() != null) r.setAcceptingOrders(req.acceptingOrders());
        return toDto(r);
    }

    // ---------- Merchant: categories ----------

    public List<CategoryDto> myCategories() {
        Restaurant r = currentUser.merchantRestaurant();
        return categoryRepository.findByRestaurantIdOrderByDisplayOrderAscNameAsc(r.getId()).stream()
                .map(c -> mapper.category(c, menuItemRepository.countByCategoryIdAndDeletedFalse(c.getId())))
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        String name = req.name().trim();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(r.getId(), name)) {
            throw ApiException.conflict("Category '" + name + "' already exists");
        }
        Category c = new Category();
        c.setRestaurant(r);
        applyCategory(c, req);
        return mapper.category(categoryRepository.save(c), 0);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        Category c = ownedCategory(id, r);
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(r.getId(), req.name().trim(), id)) {
            throw ApiException.conflict("Category '" + req.name().trim() + "' already exists");
        }
        applyCategory(c, req);
        return mapper.category(c, menuItemRepository.countByCategoryIdAndDeletedFalse(id));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Restaurant r = currentUser.merchantRestaurant();
        Category c = ownedCategory(id, r);
        if (menuItemRepository.countByCategoryIdAndDeletedFalse(id) > 0) {
            throw ApiException.conflict("Remove or move the items in this category before deleting it");
        }
        categoryRepository.delete(c);
    }

    // ---------- Merchant: menu items ----------

    public List<MenuItemDto> myMenuItems() {
        Restaurant r = currentUser.merchantRestaurant();
        return menuItemRepository.findByRestaurantIdAndDeletedFalseOrderByNameAsc(r.getId()).stream()
                .map(mapper::menuItem).toList();
    }

    @Transactional
    public MenuItemDto createMenuItem(MenuItemRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        MenuItem m = new MenuItem();
        m.setRestaurant(r);
        applyMenuItem(m, req, r);
        return mapper.menuItem(menuItemRepository.save(m));
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long id, MenuItemRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        MenuItem m = ownedItem(id, r);
        applyMenuItem(m, req, r);
        return mapper.menuItem(m);
    }

    @Transactional
    public MenuItemDto setAvailability(Long id, boolean available) {
        Restaurant r = currentUser.merchantRestaurant();
        MenuItem m = ownedItem(id, r);
        m.setAvailable(available);
        return mapper.menuItem(m);
    }

    @Transactional
    public void deleteMenuItem(Long id) {
        Restaurant r = currentUser.merchantRestaurant();
        MenuItem m = ownedItem(id, r);
        m.setDeleted(true);
        m.setAvailable(false);
    }

    // ---------- helpers ----------

    MenuResponse buildMenu(Restaurant r, boolean includeUnavailable) {
        Map<Long, MenuCategoryGroup> groups = new LinkedHashMap<>();
        List<Category> categories = categoryRepository.findByRestaurantIdOrderByDisplayOrderAscNameAsc(r.getId());
        for (Category c : categories) {
            if (includeUnavailable || c.isActive()) {
                groups.put(c.getId(), new MenuCategoryGroup(c.getId(), c.getName(), c.getDescription(), new java.util.ArrayList<>()));
            }
        }
        for (MenuItem m : menuItemRepository.findByRestaurantIdAndDeletedFalseOrderByNameAsc(r.getId())) {
            MenuCategoryGroup g = groups.get(m.getCategory().getId());
            if (g != null && (includeUnavailable || m.isAvailable())) {
                g.items().add(mapper.menuItem(m));
            }
        }
        List<MenuCategoryGroup> result = groups.values().stream()
                .filter(g -> !g.items().isEmpty())
                .peek(g -> g.items().sort(Comparator.comparing(MenuItemDto::name, String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return new MenuResponse(toDto(r), result);
    }

    private Category ownedCategory(Long id, Restaurant r) {
        return categoryRepository.findByIdAndRestaurantId(id, r.getId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));
    }

    private MenuItem ownedItem(Long id, Restaurant r) {
        return menuItemRepository.findByIdAndRestaurantIdAndDeletedFalse(id, r.getId())
                .orElseThrow(() -> ApiException.notFound("Menu item not found"));
    }

    private void applyCategory(Category c, CategoryRequest req) {
        c.setName(req.name().trim());
        c.setDescription(blankToNull(req.description()));
        if (req.displayOrder() != null) c.setDisplayOrder(req.displayOrder());
        if (req.active() != null) c.setActive(req.active());
    }

    private void applyMenuItem(MenuItem m, MenuItemRequest req, Restaurant r) {
        m.setCategory(ownedCategory(req.categoryId(), r));
        m.setName(req.name().trim());
        m.setDescription(blankToNull(req.description()));
        m.setPrice(req.price().setScale(2, RoundingMode.HALF_UP));
        if (req.vegetarian() != null) m.setVegetarian(req.vegetarian());
        if (req.available() != null) m.setAvailable(req.available());
        m.setImageUrl(safeUrl(req.imageUrl()));
    }

    /** Only http(s) image URLs are accepted to avoid javascript: or data: injection. */
    private static String safeUrl(String url) {
        String u = blankToNull(url);
        if (u == null) return null;
        String lower = u.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw ApiException.badRequest("Image URL must start with http:// or https://");
        }
        return u;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
