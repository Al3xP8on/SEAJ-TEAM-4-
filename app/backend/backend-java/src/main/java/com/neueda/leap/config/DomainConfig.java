// package com.neueda.trading.app.config;
//
// import com.neueda.trading.app.service.IdempotencyKeyStore;
// import com.neueda.trading.app.service.OrderPlacementService;
// import com.neueda.trading.app.service.OrderProcessor;
// import com.neueda.trading.app.service.OrderValidator;
// import com.neueda.trading.app.service.StandardOrderValidator;
// import com.neueda.trading.app.time.Clock;
// import com.neueda.trading.app.time.SystemClock;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// @Configuration
// public class DomainConfig {
//
//     @Bean
//     public Clock clock() {
//         return SystemClock.getInstance();
//     }
//
//     @Bean
//     public OrderValidator orderValidator() {
//         return new StandardOrderValidator();
//     }
//
//     @Bean
//     public OrderPlacementService settlementProcessor(OrderValidator validator, Clock clock) {
//         IdempotencyKeyStore alreadyChecked = idempotencyKey -> true;
//         return new OrderProcessor(validator, alreadyChecked, clock);
//     }
// }
