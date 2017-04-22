package org.factcast.core.store.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableList;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@ToString
public class ClientSubscriptionRequest implements SubscriptionRequest {

	long maxLatencyInMillis = 100;
	boolean continous;
	UUID startingAfter;
	List<FactSpec> specs = new LinkedList<>();
	boolean idOnly = false;

	@RequiredArgsConstructor
	public static class Builder implements SpecBuilder, TypeBuilder {
		private final ClientSubscriptionRequest toBuild;

		@Override
		public SpecBuilder or(@NonNull FactSpec s) {
			toBuild.specs.add(s);
			return this;
		}

		@Override
		public SubscriptionRequest sinceInception() {
			toBuild.lock();
			return toBuild;
		}

		@Override
		public SubscriptionRequest since(@NonNull UUID id) {
			toBuild.startingAfter = id;
			toBuild.lock();
			return toBuild;
		}

		SpecBuilder follow(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = true;
			return this;
		}

		SpecBuilder catchup(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = false;
			return this;
		}

		@Override
		public TypeBuilder asFacts() {
			toBuild.idOnly = false;
			return this;
		}

		@Override
		public TypeBuilder asIds() {
			toBuild.idOnly = true;
			return this;
		}
	}

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

	protected void lock() {
		specs = ImmutableList.copyOf(specs);
	}

	public interface SpecBuilder {
		SpecBuilder or(@NonNull FactSpec s);

		TypeBuilder asFacts();

		TypeBuilder asIds();

	}

	public interface TypeBuilder {

		SubscriptionRequest since(@NonNull UUID id);

		SubscriptionRequest sinceInception();

	}

}
