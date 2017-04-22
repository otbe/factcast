package org.factcast.store.pgsql.internal;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.FactStoreObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.eventbus.EventBus;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Wither;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
public class PGQueryIntegrationIT {

	private static final FactSpec DEFAULT_SPEC = FactSpec.ns("default-ns").type("type1");

	@Wither
	@AllArgsConstructor
	@RequiredArgsConstructor
	public static class TestHeader {
		String id = UUID.randomUUID().toString();
		String ns = "default-ns";
		String type = "type1";

		@Override
		public String toString() {
			return String.format("{\"id\":\"%s\",\"ns\":\"%s\",\"type\":\"%s\"}", id, ns, type);
		}

		public static TestHeader create() {
			return new TestHeader();
		}
	}

	@Autowired
	PGSubscriptionFactory pq;
	@Autowired
	JdbcTemplate tpl;
	@Autowired
	PGListener listener;
	@Autowired
	PGFactStore store;

	@Bean
	@Primary
	public EventBus eventBus() {
		return new EventBus(this.getClass().getSimpleName());
	}

	@Test
	@DirtiesContext
	public void testRoundtrip() throws Exception {
		SubscriptionRequest req = SubscriptionRequest.catchup(DEFAULT_SPEC).asFacts().sinceInception();

		FactStoreObserver c = mock(FactStoreObserver.class);

		pq.subscribe(req, c);

		verify(c, never()).onNext(anyObject());
		verify(c).onCatchup();
		verify(c).onComplete();

	}

	@Test
	@DirtiesContext
	public void testRoundtripInsertBefore() throws Exception {

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create().withNs("other-ns"));
		insertTestFact(TestHeader.create().withType("type2"));
		insertTestFact(TestHeader.create().withNs("other-ns").withType("type2"));

		SubscriptionRequest req = SubscriptionRequest.catchup(DEFAULT_SPEC).asFacts().sinceInception();

		FactStoreObserver c = mock(FactStoreObserver.class);

		pq.subscribe(req, c);

		verify(c).onCatchup();
		verify(c).onComplete();
		verify(c, times(2)).onNext(anyObject());
	}

	private void insertTestFact(TestHeader header) {
		tpl.execute("INSERT INTO fact(header,payload) VALUES ('" + header + "','{}')");
	}

	@Test
	@DirtiesContext()
	public void testRoundtripInsertAfter() throws Exception {
		SubscriptionRequest req = SubscriptionRequest.follow(DEFAULT_SPEC).asFacts().sinceInception();

		FactStoreObserver c = mock(FactStoreObserver.class);

		pq.subscribe(req, c);

		verify(c).onCatchup();
		verify(c, never()).onNext(any(Fact.class));

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create().withNs("other-ns"));
		insertTestFact(TestHeader.create().withType("type2"));
		insertTestFact(TestHeader.create().withNs("other-ns").withType("type2"));

		sleep(200);
		verify(c, times(2)).onNext(any(Fact.class));

	}

	@Test
	@DirtiesContext()
	public void testRoundtripCatchupEventsInsertedAfterStart() throws Exception {

		SubscriptionRequest req = SubscriptionRequest.follow(DEFAULT_SPEC).asFacts().sinceInception();
		FactStoreObserver c = mock(FactStoreObserver.class);
		doAnswer(i -> {
			sleep(50);
			return null;
		}).when(c).onNext(any());
		ExecutorService es = Executors.newCachedThreadPool();

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());

		Future<?> connected = es.submit(() -> pq.subscribe(req, c));

		Thread.sleep(200);

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		connected.get();

		Thread.sleep(1000);

		verify(c).onCatchup();
		verify(c, times(8)).onNext(any(Fact.class));

		sleep(200);
		insertTestFact(TestHeader.create());

		sleep(300);

		verify(c, times(9)).onNext(any(Fact.class));

	}

	// TODO remove all the Thread.sleeps
	private void sleep(long ms) throws InterruptedException {
		Thread.sleep(ms);
	}

	@Test
	@DirtiesContext()
	public void testRoundtripCompletion() throws Exception {

		SubscriptionRequest req = SubscriptionRequest.catchup(DEFAULT_SPEC).asFacts().sinceInception();
		FactStoreObserver c = mock(FactStoreObserver.class);

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());

		pq.subscribe(req, c);

		verify(c).onCatchup();
		verify(c).onComplete();
		verify(c, times(5)).onNext(any(Fact.class));

		insertTestFact(TestHeader.create());

		sleep(300);

		verify(c).onCatchup();
		verify(c).onComplete();
		verify(c, times(5)).onNext(any(Fact.class));

	}

	@Test
	@DirtiesContext()
	public void testCancel() throws Exception {

		SubscriptionRequest req = SubscriptionRequest.follow(DEFAULT_SPEC).asFacts().sinceInception();

		FactStoreObserver c = mock(FactStoreObserver.class);

		insertTestFact(TestHeader.create());

		Subscription sub = pq.subscribe(req, c);

		verify(c).onCatchup();
		verify(c, times(1)).onNext(anyObject());

		insertTestFact(TestHeader.create());
		insertTestFact(TestHeader.create());
		sleep(200);
		verify(c, times(3)).onNext(anyObject());

		sub.close();

		insertTestFact(TestHeader.create()); // must not show up
		insertTestFact(TestHeader.create());// must not show up
		sleep(200);
		verify(c, times(3)).onNext(anyObject());

	}

}
