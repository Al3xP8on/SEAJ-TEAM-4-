package com.neueda.leap.services;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ExchangeRatesServiceTest {
 @Test void cachesRatesAndRetainsDatedValuesDuringOutage() {
  var http = new RestTemplate(); var server = MockRestServiceServer.createServer(http);
  Clock clock = mock(Clock.class); Instant now = Instant.parse("2026-10-19T12:00:00Z"); when(clock.instant()).thenReturn(now);
  var service = new ExchangeRatesService(http, clock);
  server.expect(requestTo(ExchangeRatesService.URL)).andRespond(withSuccess("{\"base\":\"USD\",\"date\":\"2026-10-16\",\"rates\":{\"GBP\":0.75,\"EUR\":-1}}", MediaType.APPLICATION_JSON));
  server.expect(requestTo(ExchangeRatesService.URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
  var rates = service.latest(); assertEquals(0.75, rates.rates().get("GBP").doubleValue()); assertFalse(rates.rates().containsKey("EUR")); assertSame(rates, service.latest());
  when(clock.instant()).thenReturn(now.plusSeconds(3601)); var stale = service.latest(); assertEquals(rates.date(), stale.date()); assertEquals(rates.rates(), stale.rates()); assertFalse(stale.message().isBlank()); server.verify();
 }
 @Test void rejectsInvalidRatesWithoutFabricatingConversions() {
  var http = new RestTemplate(); var server = MockRestServiceServer.createServer(http); var service = new ExchangeRatesService(http, Clock.systemUTC());
  server.expect(requestTo(ExchangeRatesService.URL)).andRespond(withSuccess("{\"base\":\"EUR\",\"date\":\"2026-10-16\",\"rates\":{\"GBP\":0.75}}", MediaType.APPLICATION_JSON));
  assertThrows(org.springframework.web.server.ResponseStatusException.class, service::latest); server.verify();
 }
 @Test void returnsSortedValidatedForexHistoryAndCachesIt() {
  var http = new RestTemplate(); var server = MockRestServiceServer.createServer(http);
  var service = new ExchangeRatesService(http, Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
  server.expect(requestTo("https://api.frankfurter.dev/v1/2026-06-21..2026-10-19?base=USD&symbols=GBP,EUR,CHF,JPY,CAD,AUD"))
   .andRespond(withSuccess("{\"base\":\"USD\",\"rates\":{\"2026-10-16\":{\"EUR\":0.9},\"2026-10-15\":{\"EUR\":0.8,\"JPY\":-1}}}", MediaType.APPLICATION_JSON));
  var data = service.history();
  assertEquals(2, data.sessions().size());
  assertEquals("2026-10-15", data.sessions().get(0).date());
  assertFalse(data.sessions().get(0).rates().containsKey("JPY"));
  assertSame(data, service.history()); server.verify();
 }
}
