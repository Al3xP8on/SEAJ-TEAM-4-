package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.services.OrderSettlementService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionListenerTest {
    private final OrderSettlementService settlement = mock(OrderSettlementService.class);
    private final ExecutionListener listener = new ExecutionListener(
        new ObjectMapper().registerModule(new JavaTimeModule()), settlement);
    @Test void delegatesReportsToTransactionalSettlement() throws Exception {
        listener.onExecution("{}");
        verify(settlement).settle(any(ExecutionEvent.class));
    }
    @Test void unreadableReportsAreNotSilentlyAcknowledged() {
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> listener.onExecution("not-json"));
        verifyNoInteractions(settlement);
    }
    @Test void settlementFailurePropagatesSoKafkaCanRedeliver() {
        doThrow(new IllegalStateException("database unavailable")).when(settlement).settle(any());
        assertThrows(IllegalStateException.class, () -> listener.onExecution("{}"));
    }
}
