package simple.simple_webapp.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderManagement {

	private final ApplicationEventPublisher events;

	public OrderManagement(ApplicationEventPublisher events) {
		this.events = events;
	}

	@Transactional
	public void complete() {
		events.publishEvent(new OrderCompleted(UUID.randomUUID()));
	}
}