package com.restaurant.rms.service;

import com.restaurant.rms.config.AppProperties;
import com.restaurant.rms.dto.AuthDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.Restaurant;
import com.restaurant.rms.model.Role;
import com.restaurant.rms.model.User;
import com.restaurant.rms.repository.RestaurantRepository;
import com.restaurant.rms.repository.UserRepository;
import com.restaurant.rms.security.JwtService;
import com.restaurant.rms.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final DtoMapper mapper;
    private final LoyaltyService loyaltyService;
    private final CurrentUserService currentUser;
    private final AppProperties props;

    public AuthService(UserRepository userRepository, RestaurantRepository restaurantRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService, DtoMapper mapper, LoyaltyService loyaltyService,
                       CurrentUserService currentUser, AppProperties props) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.loyaltyService = loyaltyService;
        this.currentUser = currentUser;
        this.props = props;
    }

    @Transactional
    public AuthResponse signupCustomer(CustomerSignupRequest req) {
        User user = newUser(req.name(), req.email(), req.mobile(), req.password(), Role.CUSTOMER);
        user.setAddress(trim(req.address()));
        user.setNotificationConsent(req.notificationConsent() == null || req.notificationConsent());
        user = userRepository.save(user);
        int bonus = props.getLoyalty().getSignupBonus();
        if (bonus > 0) {
            loyaltyService.credit(user, null, bonus, com.restaurant.rms.model.LoyaltyType.BONUS, "Welcome bonus");
        }
        return tokenFor(user);
    }

    @Transactional
    public AuthResponse signupMerchant(MerchantSignupRequest req) {
        User user = userRepository.save(newUser(req.name(), req.email(), req.mobile(), req.password(), Role.MERCHANT));
        Restaurant r = new Restaurant();
        r.setOwner(user);
        r.setName(req.restaurantName().trim());
        r.setAddress(trim(req.restaurantAddress()));
        r.setCuisine(trim(req.cuisine()));
        r.setPhone(user.getMobile());
        r.setEmail(user.getEmail());
        if (req.taxPercent() != null) r.setTaxPercent(req.taxPercent());
        restaurantRepository.save(r);
        return tokenFor(user);
    }

    public AuthResponse signin(SigninRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.login().trim(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return tokenFor(user);
    }

    public UserDto me() {
        return mapper.user(currentUser.user());
    }

    @Transactional
    public UserDto updateProfile(UpdateProfileRequest req) {
        User u = currentUser.user();
        String mobile = req.mobile().trim();
        if (!mobile.equals(u.getMobile()) && userRepository.existsByMobile(mobile)) {
            throw ApiException.conflict("Mobile number is already registered");
        }
        u.setName(req.name().trim());
        u.setMobile(mobile);
        u.setAddress(trim(req.address()));
        if (req.notificationConsent() != null) u.setNotificationConsent(req.notificationConsent());
        return mapper.user(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User u = currentUser.user();
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    // ---------- Kitchen staff (managed by merchant) ----------

    public List<StaffDto> listStaff() {
        Restaurant r = currentUser.merchantRestaurant();
        return userRepository.findByRestaurantIdAndRole(r.getId(), Role.KITCHEN).stream().map(mapper::staff).toList();
    }

    @Transactional
    public StaffDto createStaff(StaffRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        User staff = newUser(req.name(), req.email(), req.mobile(), req.password(), Role.KITCHEN);
        staff.setRestaurant(r);
        staff.setNotificationConsent(false);
        return mapper.staff(userRepository.save(staff));
    }

    @Transactional
    public StaffDto setStaffEnabled(Long staffId, boolean enabled) {
        Restaurant r = currentUser.merchantRestaurant();
        User staff = userRepository.findById(staffId)
                .filter(s -> s.getRole() == Role.KITCHEN && s.getRestaurant() != null
                        && s.getRestaurant().getId().equals(r.getId()))
                .orElseThrow(() -> ApiException.notFound("Staff member not found"));
        staff.setEnabled(enabled);
        return mapper.staff(staff);
    }

    private User newUser(String name, String email, String mobile, String password, Role role) {
        String normEmail = email.trim().toLowerCase();
        String normMobile = mobile.trim();
        if (userRepository.existsByEmailIgnoreCase(normEmail)) {
            throw ApiException.conflict("Email is already registered");
        }
        if (userRepository.existsByMobile(normMobile)) {
            throw ApiException.conflict("Mobile number is already registered");
        }
        User u = new User();
        u.setName(name.trim());
        u.setEmail(normEmail);
        u.setMobile(normMobile);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        return u;
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.generateToken(new UserPrincipal(user));
        return new AuthResponse(token, jwtService.getExpirationMs(), mapper.user(user));
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @SuppressWarnings("unused")
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
