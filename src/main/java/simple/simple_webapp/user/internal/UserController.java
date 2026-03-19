package simple.simple_webapp.user.internal;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import simple.simple_webapp.user.CreateUser;
import simple.simple_webapp.user.DuplicateEmailException;
import simple.simple_webapp.user.UserManagement;

@Controller
class UserController {

    private final UserManagement userManagement;

    UserController(UserManagement userManagement) {
        this.userManagement = userManagement;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    String register(@RequestParam String email, @RequestParam String password, Model model) {
        try {
            userManagement.register(new CreateUser(email, password));
            return "redirect:/login?check-email";
        } catch (DuplicateEmailException e) {
            model.addAttribute("error", "Email already registered");
            return "register";
        }
    }

    @GetMapping("/activate")
    String activate(@RequestParam String token) {
        try {
            userManagement.activateUser(token);
            return "redirect:/login?activated";
        } catch (IllegalArgumentException e) {
            return "redirect:/login?activation-failed";
        }
    }

    @GetMapping("/account/change-password")
    String changePasswordForm() {
        return "account/change-password";
    }

    @PostMapping("/account/change-password")
    String changePassword(@RequestParam String currentPassword,
                          @RequestParam String newPassword,
                          @AuthenticationPrincipal UserDetails user,
                          Model model) {
        try {
            userManagement.changePassword(user.getUsername(), currentPassword, newPassword);
            return "redirect:/account/change-password?success";
        } catch (BadCredentialsException e) {
            model.addAttribute("error", "Current password is incorrect");
            return "account/change-password";
        }
    }
}
