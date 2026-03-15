package simple.simple_webapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.testcontainers.junit.jupiter.Testcontainers;
import simple.simple_webapp.inventory.InventoryUpdated;
import simple.simple_webapp.order.OrderManagement;

import java.util.Collection;

@SpringBootTest
@EnableScenarios
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class ApplicationIntegrationTests {

    @Autowired
    OrderManagement orders;
    @Autowired
    EventPublicationRegistry registry;

    @Test
    void bootstrapsApplication(Scenario scenario) throws Exception {

        scenario.stimulate(() -> orders.complete())
                .andWaitForStateChange(() -> registry.findIncompletePublications(), Collection::isEmpty)
                .andExpect(InventoryUpdated.class)
                .toArrive();
    }
}