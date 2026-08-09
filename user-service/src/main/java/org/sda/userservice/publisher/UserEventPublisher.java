package org.sda.userservice.publisher;

import org.sda.userservice.dto.event.UserRegisteredEvent;
import org.sda.userservice.entity.Role;
import org.sda.userservice.entity.User;
import org.sda.userservice.messaging.Constants;
import org.sda.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * The only outbound channel this service has. User Service makes no REST calls to any other
 * service - it announces new accounts here and whoever cares binds a queue to it.
 *
 * <p>Also re-announces every existing admin at startup. Notification Service uses
 * {@code user.registered} to learn who the admins are (so it can tell them about new orders and
 * finished deliveries), and the replay makes that knowledge self-healing: an admin registered
 * before this event existed, or a registration message lost while the broker was down, is fixed
 * by the next restart.
 */
@Component
public class UserEventPublisher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final UserRepository userRepository;

    public UserEventPublisher(AmqpTemplate amqpTemplate, UserRepository userRepository) {
        this.amqpTemplate = amqpTemplate;
        this.userRepository = userRepository;
    }

    public void publishRegistered(User user) {
        try {
            amqpTemplate.convertAndSend(Constants.EXCHANGE, Constants.RK_USER_REGISTERED,
                    new UserRegisteredEvent(user.getId(), user.getName(), user.getEmail(), user.getRole().name()));
            log.info("Published {} for user {}", Constants.RK_USER_REGISTERED, user.getId());
        } catch (RuntimeException e) {
            // Signup must never fail because the broker is down; admins are replayed at startup.
            log.warn("Could not publish {}: {}", Constants.RK_USER_REGISTERED, e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (User admin : userRepository.findByRole(Role.ADMIN)) {
                amqpTemplate.convertAndSend(Constants.EXCHANGE, Constants.RK_USER_REGISTERED,
                        new UserRegisteredEvent(admin.getId(), admin.getName(), admin.getEmail(), Role.ADMIN.name()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not replay admin registrations: {}", e.getMessage());
        }
    }
}
