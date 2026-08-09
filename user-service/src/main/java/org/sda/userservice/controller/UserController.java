package org.sda.userservice.controller;

import org.sda.userservice.dto.AddressRequest;
import org.sda.userservice.dto.UserResponse;
import org.sda.userservice.entity.Role;
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
        User user = userService.signup(request.name(), request.email(), request.phone(), request.password(),
                request.role(), request.vehicleType(), request.licenseNumber(), request.restaurantId());
        return new AuthResponse(userService.issueToken(user), UserResponse.from(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        User user = userService.login(request.identifier(), request.password());
        return new AuthResponse(userService.issueToken(user), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse getProfile(@RequestHeader("Authorization") String authHeader) {
        return UserResponse.from(userService.getCurrentUser(authHeader));
    }

    @PutMapping("/me")
    public UserResponse updateProfile(@RequestHeader("Authorization") String authHeader, @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.updateProfile(authHeader, request.name(), request.phone(), request.photo()));
    }

    @GetMapping("/me/addresses")
    public List<UserResponse.AddressView> getAddresses(@RequestHeader("Authorization") String authHeader) {
        return userService.getAddresses(authHeader).stream().map(UserResponse.AddressView::from).toList();
    }

    @PostMapping("/me/addresses")
    public UserResponse addAddress(@RequestHeader("Authorization") String authHeader, @RequestBody AddressRequest address) {
        return UserResponse.from(userService.addAddress(authHeader, address.toEntity()));
    }

    @PutMapping("/me/addresses/{addressId}")
    public UserResponse updateAddress(@RequestHeader("Authorization") String authHeader,
                               @PathVariable String addressId,
                               @RequestBody AddressRequest address) {
        return UserResponse.from(userService.updateAddress(authHeader, addressId, address.toEntity()));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public UserResponse deleteAddress(@RequestHeader("Authorization") String authHeader, @PathVariable String addressId) {
        return UserResponse.from(userService.deleteAddress(authHeader, addressId));
    }

    @PutMapping("/me/addresses/{addressId}/default")
    public UserResponse setDefaultAddress(@RequestHeader("Authorization") String authHeader, @PathVariable String addressId) {
        return UserResponse.from(userService.setDefaultAddress(authHeader, addressId));
    }

    @PutMapping("/me/preferences")
    public UserResponse updatePreferences(@RequestHeader("Authorization") String authHeader, @RequestBody PreferencesRequest request) {
        return UserResponse.from(userService.updatePreferences(authHeader, request.foodPreferences(), request.dietaryTags(), request.defaultPaymentMethod()));
    }

    @GetMapping
    public List<UserResponse> listUsers(@RequestHeader("Authorization") String authHeader) {
        return userService.listUsers(authHeader).stream().map(UserResponse::from).toList();
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(@RequestHeader("Authorization") String authHeader, @PathVariable String userId) {
        userService.deleteUser(authHeader, userId);
    }

    public record SignupRequest(String name, String email, String phone, String password, Role role,
                                 String vehicleType, String licenseNumber, String restaurantId) {
    }

    public record LoginRequest(String identifier, String password) {
    }

    public record UpdateProfileRequest(String name, String phone, String photo) {
    }

    public record PreferencesRequest(List<String> foodPreferences, List<String> dietaryTags, String defaultPaymentMethod) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
