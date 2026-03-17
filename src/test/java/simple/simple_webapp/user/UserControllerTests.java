package simple.simple_webapp.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class UserControllerTests {

    @Autowired WebApplicationContext context;
    @MockitoBean UserManagement userManagement;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void loginPageReturns200() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void registerPageReturns200() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void registerRedirectsToLoginOnSuccess() throws Exception {
        mockMvc.perform(post("/register")
                        .param("email", "alice@example.com")
                        .param("password", "secret")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?check-email"));
    }

    @Test
    void registerShowsErrorOnDuplicateEmail() throws Exception {
        doThrow(new DuplicateEmailException("alice@example.com"))
                .when(userManagement).register("alice@example.com", "secret");

        mockMvc.perform(post("/register")
                        .param("email", "alice@example.com")
                        .param("password", "secret")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void activateWithValidTokenRedirects() throws Exception {
        mockMvc.perform(get("/activate").param("token", "valid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activated"));
    }

    @Test
    void activateWithInvalidTokenRedirects() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired token"))
                .when(userManagement).activateUser("bad-token");

        mockMvc.perform(get("/activate").param("token", "bad-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activation-failed"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void changePasswordFormReturns200() throws Exception {
        mockMvc.perform(get("/account/change-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/change-password"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void changePasswordSuccessRedirects() throws Exception {
        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "old")
                        .param("newPassword", "new")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password?success"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void changePasswordWrongCurrentReturnsError() throws Exception {
        doThrow(new org.springframework.security.authentication.BadCredentialsException("bad"))
                .when(userManagement).changePassword("alice", "wrong", "new");

        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "wrong")
                        .param("newPassword", "new")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("account/change-password"))
                .andExpect(model().attributeExists("error"));
    }
}