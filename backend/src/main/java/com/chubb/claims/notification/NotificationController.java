package com.chubb.claims.notification;

import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.RequireRole;
import com.chubb.claims.common.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequireRole(UserRole.CLAIMANT)
@Tag(name = "Claimant")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/me/notifications")
    @Operation(summary = "My notifications, newest first",
            description = "Created asynchronously from claim events, so a new one can take a moment to appear.")
    public List<NotificationResponse> myNotifications(CurrentUser user) {
        return notificationService.notificationsFor(user.id());
    }
}
