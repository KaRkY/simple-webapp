package simple.simple_webapp.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import simple.simple_webapp.order.OrderCompleted;

@Service
class InventoryManagement {

	private static final Logger LOG = LoggerFactory.getLogger(InventoryManagement.class);

	private final ApplicationEventPublisher events;

	public InventoryManagement(ApplicationEventPublisher events) {
		this.events = events;
	}

	@ApplicationModuleListener
	void on(OrderCompleted event) throws InterruptedException {

		var orderId = event.orderId();

		LOG.info("Received order completion for {}.", orderId);

		// Simulate busy work
		Thread.sleep(1000);
		events.publishEvent(new InventoryUpdated(orderId));

		LOG.info("Finished order completion for {}.", orderId);
	}
}