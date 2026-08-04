package org.sda.userservice.service;

import io.jsonwebtoken.JwtException;
import org.sda.userservice.entity.Address;
import org.sda.userservice.entity.User;
import org.sda.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User signup(String name, String email, String phone, String password) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setProvider("LOCAL");
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);
        saved.setPassword(null);
        return saved;
    }

    public User login(String identifier, String password) {
        if (identifier == null || password == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByPhone(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        user.setPassword(null);
        return user;
    }

    public User socialLogin(String provider, String providerId, String email, String name) {
        if (provider == null || providerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provider and providerId are required");
        }
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setName(name);
                    newUser.setEmail(email);
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);
                    newUser.setCreatedAt(Instant.now());
                    return userRepository.save(newUser);
                });
        user.setPassword(null);
        return user;
    }

    public String issueToken(User user) {
        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    public User getCurrentUser(String authHeader) {
        User user = loadUserFromToken(authHeader);
        user.setPassword(null);
        return user;
    }

    public User updateProfile(String authHeader, String name, String phone, String photo) {
        User user = loadUserFromToken(authHeader);
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone);
        }
        if (photo != null) {
            user.setPhoto(photo);
        }
        return saveAndSanitize(user);
    }

    public List<Address> getAddresses(String authHeader) {
        return loadUserFromToken(authHeader).getAddresses();
    }

    public User addAddress(String authHeader, Address address) {
        User user = loadUserFromToken(authHeader);
        address.setId(UUID.randomUUID().toString());
        if (user.getAddresses().isEmpty()) {
            address.setDefaultAddress(true);
        }
        user.getAddresses().add(address);
        return saveAndSanitize(user);
    }

    public User updateAddress(String authHeader, String addressId, Address updated) {
        User user = loadUserFromToken(authHeader);
        Address existing = user.getAddresses().stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        existing.setLabel(updated.getLabel());
        existing.setStreet(updated.getStreet());
        existing.setCity(updated.getCity());
        existing.setArea(updated.getArea());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        return saveAndSanitize(user);
    }

    public User deleteAddress(String authHeader, String addressId) {
        User user = loadUserFromToken(authHeader);
        boolean removed = user.getAddresses().removeIf(a -> a.getId().equals(addressId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        if (!user.getAddresses().isEmpty() && user.getAddresses().stream().noneMatch(Address::isDefaultAddress)) {
            user.getAddresses().get(0).setDefaultAddress(true);
        }
        return saveAndSanitize(user);
    }

    public User setDefaultAddress(String authHeader, String addressId) {
        User user = loadUserFromToken(authHeader);
        boolean found = false;
        for (Address address : user.getAddresses()) {
            boolean isMatch = address.getId().equals(addressId);
            address.setDefaultAddress(isMatch);
            found = found || isMatch;
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        return saveAndSanitize(user);
    }

    public User updatePreferences(String authHeader, List<String> foodPreferences, List<String> dietaryTags, String defaultPaymentMethod) {
        User user = loadUserFromToken(authHeader);
        if (foodPreferences != null) {
            user.setFoodPreferences(foodPreferences);
        }
        if (dietaryTags != null) {
            user.setDietaryTags(dietaryTags);
        }
        if (defaultPaymentMethod != null) {
            user.setDefaultPaymentMethod(defaultPaymentMethod);
        }
        return saveAndSanitize(user);
    }

    private User saveAndSanitize(User user) {
        User saved = userRepository.save(user);
        saved.setPassword(null);
        return saved;
    }

    private User loadUserFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
