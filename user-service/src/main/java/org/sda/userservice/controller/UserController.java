package org.sda.userservice.controller;

import org.sda.userservice.entity.Address;
import org.sda.userservice.entity.User;
import org.sda.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SignupRequest request) {
        User user = userService.signup(request.name(), request.email(), request.phone(), request.password());
        return new AuthResponse(userService.issueToken(user), user);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        User user = userService.login(request.identifier(), request.password());
        return new AuthResponse(userService.issueToken(user), user);
    }

    @GetMapping("/me")
    public User getProfile(@RequestHeader("Authorization") String authHeader) {
        return userService.getCurrentUser(authHeader);
    }

    @PutMapping("/me")
    public User updateProfile(@RequestHeader("Authorization") String authHeader, @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authHeader, request.name(), request.phone(), request.photo());
    }

    @GetMapping("/me/addresses")
    public List<Address> getAddresses(@RequestHeader("Authorization") String authHeader) {
        return userService.getAddresses(authHeader);
    }

    @PostMapping("/me/addresses")
    public User addAddress(@RequestHeader("Authorization") String authHeader, @RequestBody Address address) {
        return userService.addAddress(authHeader, address);
    }

    @PutMapping("/me/addresses/{addressId}")
    public User updateAddress(@RequestHeader("Authorization") String authHeader,
                               @PathVariable String addressId,
                               @RequestBody Address address) {
        return userService.updateAddress(authHeader, addressId, address);
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public User deleteAddress(@RequestHeader("Authorization") String authHeader, @PathVariable String addressId) {
        return userService.deleteAddress(authHeader, addressId);
    }

    @PutMapping("/me/addresses/{addressId}/default")
    public User setDefaultAddress(@RequestHeader("Authorization") String authHeader, @PathVariable String addressId) {
        return userService.setDefaultAddress(authHeader, addressId);
    }

    @PutMapping("/me/preferences")
    public User updatePreferences(@RequestHeader("Authorization") String authHeader, @RequestBody PreferencesRequest request) {
        return userService.updatePreferences(authHeader, request.foodPreferences(), request.dietaryTags(), request.defaultPaymentMethod());
    }

    public record SignupRequest(String name, String email, String phone, String password) {
    }

    public record LoginRequest(String identifier, String password) {
    }

    public record UpdateProfileRequest(String name, String phone, String photo) {
    }

    public record PreferencesRequest(List<String> foodPreferences, List<String> dietaryTags, String defaultPaymentMethod) {
    }

    public record AuthResponse(String token, User user) {
    }
}
