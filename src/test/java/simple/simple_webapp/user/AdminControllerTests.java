package simple.simple_webapp.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.TestcontainersConfiguration;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class AdminControllerTests {

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
    @WithMockUser(roles = "ADMIN")
    void listUsersAsAdminReturns200() throws Exception {
        when(userManagement.findAll(false)).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listUsersAsUserReturnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void lockUserWithHxRequestReturnsPartialRow() throws Exception {
        var id = UUID.randomUUID();
        when(userManagement.findById(id))
                .thenReturn(new UserSummary(id, "bob@example.com", List.of("USER"), false, true, false, true));

        mockMvc.perform(post("/admin/users/{id}/lock", id).with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void lockUserWithoutHxRequestRedirects() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/lock", id).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void deleteOwnAccountReturnsForbidden() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Cannot delete own account"))
                .when(userManagement).deleteUser(id, "admin");

        mockMvc.perform(delete("/admin/users/{id}", id).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void deleteOtherUserReturns200() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/admin/users/{id}", id).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPasswordWithHxRequestReturnsPartialRow() throws Exception {
        var id = UUID.randomUUID();
        when(userManagement.resetPassword(id)).thenReturn("temp-pass-123");
        when(userManagement.findById(id))
                .thenReturn(new UserSummary(id, "bob@example.com", List.of("USER"), true, true, false, true));

        mockMvc.perform(post("/admin/users/{id}/reset-password", id).with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPasswordWithoutHxRequestRedirects() throws Exception {
        var id = UUID.randomUUID();
        when(userManagement.resetPassword(id)).thenReturn("temp-pass-123");

        mockMvc.perform(post("/admin/users/{id}/reset-password", id).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void deleteUserPostWithHxRequestReturns200() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/delete", id).with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void deleteUserPostWithoutHxRequestRedirects() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/delete", id).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsersWithShowDeletedReturns200() throws Exception {
        when(userManagement.findAll(true)).thenReturn(List.of());

        mockMvc.perform(get("/admin/users").param("showDeleted", "true"))
                .andExpect(status().isOk());
    }
}