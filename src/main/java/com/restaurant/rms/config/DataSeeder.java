package com.restaurant.rms.config;

import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Seeds a demo restaurant, menu, coupons and accounts on first start (empty database only). */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final RestaurantRepository restaurants;
    private final CategoryRepository categories;
    private final MenuItemRepository items;
    private final CouponRepository coupons;
    private final LoyaltyTransactionRepository loyalty;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DataSeeder(UserRepository users, RestaurantRepository restaurants, CategoryRepository categories,
                      MenuItemRepository items, CouponRepository coupons, LoyaltyTransactionRepository loyalty,
                      PasswordEncoder encoder, @Value("${app.seed-demo-data:true}") boolean enabled) {
        this.users = users;
        this.restaurants = restaurants;
        this.categories = categories;
        this.items = items;
        this.coupons = coupons;
        this.loyalty = loyalty;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || users.count() > 0) return;

        User merchant = user("Rahul Sharma", "merchant@demo.com", "9876500001", Role.MERCHANT);
        Restaurant r = new Restaurant();
        r.setOwner(merchant);
        r.setName("Spice Garden");
        r.setDescription("Authentic North & South Indian cuisine, freshly prepared.");
        r.setCuisine("Indian");
        r.setAddress("12 MG Road, Bengaluru");
        r.setPhone("9876500001");
        r.setEmail("merchant@demo.com");
        r.setGstNumber("29ABCDE1234F1Z5");
        r.setTaxPercent(new BigDecimal("5.00"));
        restaurants.save(r);

        User kitchen = user("Kitchen Staff", "kitchen@demo.com", "9876500002", Role.KITCHEN);
        kitchen.setRestaurant(r);
        kitchen.setNotificationConsent(false);

        User customer = user("Priya Verma", "customer@demo.com", "9876500003", Role.CUSTOMER);
        customer.setAddress("45 Indiranagar, Bengaluru");
        customer.setLoyaltyPoints(100);
        LoyaltyTransaction lt = new LoyaltyTransaction();
        lt.setCustomer(customer);
        lt.setType(LoyaltyType.BONUS);
        lt.setPoints(100);
        lt.setBalanceAfter(100);
        lt.setDescription("Welcome bonus");
        loyalty.save(lt);

        Category starters = category(r, "Starters", 1);
        Category mains = category(r, "Main Course", 2);
        Category breads = category(r, "Breads", 3);
        Category south = category(r, "South Indian", 4);
        Category drinks = category(r, "Beverages", 5);
        Category desserts = category(r, "Desserts", 6);

        item(r, starters, "Paneer Tikka", "Chargrilled cottage cheese with spices", "220", true);
        item(r, starters, "Veg Spring Roll", "Crispy rolls stuffed with vegetables", "160", true);
        item(r, starters, "Chicken 65", "Spicy deep-fried chicken bites", "260", false);
        item(r, mains, "Paneer Butter Masala", "Cottage cheese in rich tomato gravy", "280", true);
        item(r, mains, "Dal Makhani", "Slow-cooked black lentils with butter", "220", true);
        item(r, mains, "Butter Chicken", "Tandoori chicken in creamy tomato gravy", "340", false);
        item(r, mains, "Veg Biryani", "Fragrant basmati rice with vegetables", "240", true);
        item(r, mains, "Chicken Biryani", "Hyderabadi dum biryani", "320", false);
        item(r, breads, "Butter Naan", "Soft tandoori bread with butter", "50", true);
        item(r, breads, "Tandoori Roti", "Whole wheat bread", "30", true);
        item(r, south, "Masala Dosa", "Crispy dosa with potato filling", "120", true);
        item(r, south, "Idli Sambar", "Steamed rice cakes (2 pcs) with sambar", "80", true);
        item(r, drinks, "Masala Chai", "Indian spiced tea", "40", true);
        item(r, drinks, "Sweet Lassi", "Chilled yogurt drink", "80", true);
        item(r, drinks, "Fresh Lime Soda", "Sweet or salted", "70", true);
        item(r, desserts, "Gulab Jamun", "2 pcs, served warm", "90", true);
        item(r, desserts, "Rasmalai", "Soft cottage cheese dumplings in saffron milk", "110", true);

        coupon(r, "WELCOME10", "10% off on orders above Rs.300 (max Rs.100)", DiscountType.PERCENT, "10", "300", "100", 1);
        coupon(r, "FLAT50", "Flat Rs.50 off on orders above Rs.500", DiscountType.FLAT, "50", "500", null, null);
        Coupon expired = coupon(r, "OLD20", "Expired coupon (for testing)", DiscountType.PERCENT, "20", "0", null, null);
        expired.setValidFrom(LocalDate.now().minusDays(30));
        expired.setValidTo(LocalDate.now().minusDays(1));

        log.info("Demo data created. Logins (password: Demo@123) -> merchant@demo.com | kitchen@demo.com | customer@demo.com");
    }

    private User user(String name, String email, String mobile, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setMobile(mobile);
        u.setRole(role);
        u.setPasswordHash(encoder.encode("Demo@123"));
        return users.save(u);
    }

    private Category category(Restaurant r, String name, int order) {
        Category c = new Category();
        c.setRestaurant(r);
        c.setName(name);
        c.setDisplayOrder(order);
        return categories.save(c);
    }

    private void item(Restaurant r, Category c, String name, String desc, String price, boolean veg) {
        MenuItem m = new MenuItem();
        m.setRestaurant(r);
        m.setCategory(c);
        m.setName(name);
        m.setDescription(desc);
        m.setPrice(new BigDecimal(price));
        m.setVegetarian(veg);
        items.save(m);
    }

    private Coupon coupon(Restaurant r, String code, String desc, DiscountType type, String value, String min,
                          String max, Integer perCustomer) {
        Coupon c = new Coupon();
        c.setRestaurant(r);
        c.setCode(code);
        c.setDescription(desc);
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setMinOrderAmount(new BigDecimal(min));
        c.setMaxDiscount(max == null ? null : new BigDecimal(max));
        c.setValidFrom(LocalDate.now().minusDays(1));
        c.setValidTo(LocalDate.now().plusYears(1));
        c.setPerCustomerLimit(perCustomer);
        return coupons.save(c);
    }
}
