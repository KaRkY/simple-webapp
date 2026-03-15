package simple.simple_webapp.order;


import org.jmolecules.event.annotation.DomainEvent;

import java.util.UUID;

@DomainEvent
public record OrderCompleted(UUID orderId) {}