package simple.simple_webapp.user.internal;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import simple.simple_webapp.user.UserManagement;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController {

    private final UserManagement userManagement;

    AdminController(UserManagement userManagement) {
        this.userManagement = userManagement;
    }

    @GetMapping("/users")
    String users(@RequestParam(defaultValue = "false") boolean showDeleted, Model model) {
        model.addAttribute("users", userManagement.findAll(showDeleted));
        model.addAttribute("showDeleted", showDeleted);
        return "admin/users";
    }

    @GetMapping("/users/list")
    String userList(Model model) {
        model.addAttribute("users", userManagement.findAll(false));
        return "admin/user-row :: userRows";
    }

    // --- Lock ---

    @PostMapping("/users/{id}/lock")
    @HxRequest
    String lockUserHtmx(@PathVariable Long id, Model model) {
        userManagement.lockUser(id);
        model.addAttribute("user", userManagement.findById(id));
        return "admin/user-row :: userRow";
    }

    @PostMapping("/users/{id}/lock")
    String lockUser(@PathVariable Long id) {
        userManagement.lockUser(id);
        return "redirect:/admin/users";
    }

    // --- Unlock ---

    @PostMapping("/users/{id}/unlock")
    @HxRequest
    String unlockUserHtmx(@PathVariable Long id, Model model) {
        userManagement.unlockUser(id);
        model.addAttribute("user", userManagement.findById(id));
        return "admin/user-row :: userRow";
    }

    @PostMapping("/users/{id}/unlock")
    String unlockUser(@PathVariable Long id) {
        userManagement.unlockUser(id);
        return "redirect:/admin/users";
    }

    // --- Role ---

    @PostMapping("/users/{id}/role")
    @HxRequest
    String setRoleHtmx(@PathVariable Long id, @RequestParam UserRole role, Model model) {
        userManagement.setRole(id, role);
        model.addAttribute("user", userManagement.findById(id));
        return "admin/user-row :: userRow";
    }

    @PostMapping("/users/{id}/role")
    String setRole(@PathVariable Long id, @RequestParam UserRole role) {
        userManagement.setRole(id, role);
        return "redirect:/admin/users";
    }

    // --- Reset password ---

    @PostMapping("/users/{id}/reset-password")
    @HxRequest
    String resetPasswordHtmx(@PathVariable Long id, Model model) {
        var tempPassword = userManagement.resetPassword(id);
        model.addAttribute("user", userManagement.findById(id));
        model.addAttribute("tempPassword", tempPassword);
        return "admin/user-row :: userRow";
    }

    @PostMapping("/users/{id}/reset-password")
    String resetPassword(@PathVariable Long id) {
        userManagement.resetPassword(id);
        return "redirect:/admin/users";
    }

    // --- Delete (HTMX) ---

    @PostMapping("/users/{id}/delete")
    @HxRequest
    @ResponseBody
    ResponseEntity<String> deleteUserHtmx(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails currentUser) {
        try {
            userManagement.deleteUser(id, currentUser.getUsername());
            return ResponseEntity.ok("");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/users/{id}/delete")
    String deleteUserFallback(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails currentUser) {
        try {
            userManagement.deleteUser(id, currentUser.getUsername());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users";
    }

    // --- Delete (legacy @DeleteMapping kept for API compatibility) ---

    @DeleteMapping("/users/{id}")
    @ResponseBody
    ResponseEntity<String> deleteUser(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails currentUser) {
        try {
            userManagement.deleteUser(id, currentUser.getUsername());
            return ResponseEntity.ok("");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}
